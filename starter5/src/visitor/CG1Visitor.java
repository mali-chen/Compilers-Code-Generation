package visitor;
import syntaxtree.*;

import java.util.*;
import java.util.ArrayList;
import java.util.List;

import errorMsg.*;
import java.io.*;


//the purpose here is to annotate things with their offsets:
// - formal parameters, with respect to the (callee) frame
// - instance variables, with respect to their slot in the object
// - methods, with respect to their slot in the v-table
// - (Optionally) generate all v-tables.
public class CG1Visitor extends Visitor
{

    // This variable will determine if we print out the offsets
    // You can turn this to false when you're sure you're generating them
    // correctly, but this can be very helpful for debugging.
    boolean PRINT_OFFSETS = true;

    // IO stream to emit code.
    CodeStream code;

    //used to track the object class, since that's
    //the root of the inheritance tree.
    private ClassDecl object;

    //current class we're visiting.
    private ClassDecl currentClass;

    ////////////////////////////////////////////////////////////
    // This is used for doing your own VMT generation.
    // Otherwise you don't need it.
    ////////////////////////////////////////////////////////////
    // to collect the array types that are referenced in the code
    private HashSet<ArrayType> arrayTypes;

    public CG1Visitor(ErrorMsg e, PrintStream out, ClassDecl Object)
    {
        code = new CodeStream(out, e);
        object = Object;
        arrayTypes = new HashSet<ArrayType>();
    }

    public Object visit(Program p)
    {
        // comment the following line out if 
        // you are doing your own vtable generation:
        VtableGenerator.generate(p, code);
        
        setOffsets(object);

        printOffsets(p);

        return null;
    }

    /**
     * Print out all of the Method and Field offsets for each class in the program.
     * If PRINT_OFFSETS is set to false, then this doesn't do anything.
     */
    void printOffsets(Program p)
    {
        if(PRINT_OFFSETS)
        {
            for(ClassDecl c : p.classDecls)
            {
                for(Decl d : c.decls)
                {
                    if(d instanceof FieldDecl v)
                    {
                        System.out.println("field " + c.name + "." + v.name + 
                                           " offset = " + v.offset);
                    }
                    else if(d instanceof MethodDecl m)
                    {
                        for(VarDecl v : m.params)
                        {
                            System.out.println(m.name + " parameter " + v.name + 
                                               " offset = " + v.offset);
                        }
                    }
                }
            }
        }
    }

    /* We can't use the standard visitor pattern for setting offsets.
     * We need to do a preorder traversal of the inheritance tree!
     * We'll start by setting the offsets for Object.
     * You need to continue the traversal with all of the subclasses.
     */
    private void setOffsets(ClassDecl c)
    {
        int numObj = 0;
        int numData = 0;

        if(c.superLink != null){
            numObj = c.superLink.numObjFields;
            numData = c.superLink.numDataFields;
        }

        // assign field offsets 
        for(Decl d: c.decls){
            if(d instanceof FieldDecl){
                FieldDecl f = (FieldDecl) d;
                if(f.type.isInt() || f.type.isBoolean()){
                    // primitives: negative offsets from the data pointer
                    // starts at -16
                    f.offset = -(16 + numData * 4);
                    numData++;
                }
                else{
                    // object reference (class, string, array, null)
                    // indexed by 4 bytes, positve offsets
                    f.offset = numObj * 4;
                    numObj++;
                }
            }
        }

        // store final counts
        c.numObjFields = numObj;
        c.numDataFields = numData;

        // count vtable entires
        int nextSlot = nextVtableSlot(c.superLink);

        for (Decl d : c.decls){
            if (d instanceof MethodDecl){
                MethodDecl m = (MethodDecl) d;
                if (m.superMethod != null){
                    // overriding: inherit parent's slot number
                    m.vtableOffset = m.superMethod.vtableOffset;
                }
                else{
                    // brand new method: assign next slot
                    m.vtableOffset = nextSlot;
                    nextSlot++;
                }
            }
        }

        // assign parameter offsets for each method in c
        for (Decl d : c.decls){
            if (d instanceof MethodDecl){
                MethodDecl m = (MethodDecl) d;
                assignParamOffsets(m);
            }
        }

        for (ClassDecl sub : c.subclasses){
            setOffsets(sub);
        }
    }

    // returns how many vtable slots are already occupied in the given class  
    private int nextVtableSlot(ClassDecl c){
        if (c == null) return 0;
 
        int count = nextVtableSlot(c.superLink);
        for (Decl d : c.decls){
            if (d instanceof MethodDecl){
                MethodDecl m = (MethodDecl) d;
                if (m.superMethod == null){
                    count++;
                }
            }
        }
        return count;
    }
 
    /**
     * assigns stack frame offsets to the parameters of 1 method
     *
     * last  param = offset 4
     * first param = offset 4 + sum(sizes of params after it)
     */
    private void assignParamOffsets(MethodDecl m){
        // Walk params right-to-left, accumulating the offset.
        int offset = 4; // first byte after the saved $ra
 
        // collect params into a list to walk in reverse
        List<VarDecl> params = new ArrayList<>();
        for (VarDecl v : m.params){
            params.add(v);
        }
 
        // assign offsets from last param to first
        for (int i = params.size() - 1; i >= 0; i--){
            VarDecl p = params.get(i);
            p.offset = offset;
            offset += paramSize(p.type);
        }
 
        // paramSize = total bytes for all params
        m.paramSize = offset - 4; // - the initial 4 to get params
    }
 
    /**
     * returns the number of bytes a parameter of the given type occupies on the call stack
     *   int  = 8 (value word + integer-tag word)
     *   bool = 4 (single word)
     *   ref  = 4 (pointer word)
     */
    private int paramSize(Type t){
        if (t.isInt()) return 8;
        return 4; // boolean, object reference, array reference
    }


    /////////////////////////////////////////////////////////////
    //
    // helper methods for generating VMTs
    //
    /////////////////////////////////////////////////////////////

    /**
     * emits the name of the class as a sequence of bytes.
     * This is used by the default implementation of toString(),
     * So, we need it as part of the VMT.
     */
    public void emitPrintName(AstNode n, String name)
    {
        // emit padding bytes for string
        for(int i = name.length()%4; 0 < i && i < 4; i++)
        {
            code.emit(n, "  .byte 0");
        }

        //print out the first character with the first bit set to 1
        //This allows the toString method to know that
        //we've reached the first character of the string.
        code.emit(n, "  .byte "+ ((int)name.charAt(0) | 0x80) +
                     " # '"+name.charAt(0)+"' with high bit set");
        for(char c : name.substring(1).toCharArray())
        {
            code.emit(n, "  .byte "+(int)c+ " # '"+c+"'");
        }
    }

    /**
     * Emit VMT for arrays.
     * Since arrays can't override methods, 
     * they have the same VMT as Object.
     */
    public void emitArrayTypeVtables()
    {
        // emit object arrays before int and bool arrays (if they exists)
        // because the garbage collector
        // needs to know if it's a data array.
        ArrayType iarr = null;
        ArrayType barr = null;
        for(ArrayType at : arrayTypes)
        {
            if(at.baseType.isInt())
            {
                iarr = at;
            }
            else if(at.baseType.isBoolean())
            {
                barr = at;
            }
            else
            {
                emitArray(at);
            }
        }
        code.emit(new IntType(-1), "dataArrayVTableStart:");
        if(iarr != null)
        {
            emitArray(iarr);
        }
        if(barr != null)
        {
            emitArray(barr);
        }
    }

    public void emitArray(ArrayType at)
    {
        emitPrintName(at, at.typeName());
        code.emit(at, "CLASS_"+at.vtableName()+":");
        code.emit(at, "  .word mth_Object_hashCode");
        code.emit(at, "  .word mth_Object_equals");
        code.emit(at, "  .word mth_Object_toString");
        code.emit(at, "END_CLASS_"+at.vtableName()+":");
    }

}


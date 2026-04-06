package visitor;

import errorMsg.*;
import java.io.*;
import syntaxtree.*;
import java.util.List;

public class CG3Visitor extends Visitor
{
    // The purpose here is to generate assembly for each Node
    // in the AST.

    // IO stream to which we will emit code
    CodeStream code;

    // current stack height
    int stack;

    public CG3Visitor(ErrorMsg e, PrintStream out)
    {
        code = new CodeStream(out, e);
        code.setVisitor3(this);
        stack = 0;
    }

    public void fakeMainStmt()
    {
        code.emit("  li $s6, 1");
        code.emit("  li $s7, 0");
        code.emit(" jal newObject");
        code.emit("  la $t0, CLASS_Main"); // put Main object on the stack
        code.emit("  sw $t0, -12($s7)");
        code.emit("  addu $sp,$sp,4");
        code.emit("  move $s2, $s7");
        code.emit("  jal mth_main_Main");
    }

    @Override
    public Object visit(Program n)
    {
        code.emit(".text");
        code.emit(".globl main");
        code.emit("main:");
        code.emit("  jal vm_init");

        //Put code for mainStmt here:
        //For now, I'll just make code that calls Main_main
        //but you'll need to replace this.
        // fakeMainStmt();
        n.mainStmt.accept(this);

        //exit the program
        code.emit("  li $v0, 10");
        code.emit("  syscall");

        // This is a fake main method until you get MethodDeclVoid working.
        // When that's working you should remove these two lines.
        // code.emit("mth_main_Main:");
        // code.emit("  jr $ra");

        // emit code for all the methods in all the class declarations
        n.classDecls.accept(this);

        // flush the output and return
        code.flush();
        return null;
    }

    // method declarations
    // void method
    @Override
    public Object visit(MethodDeclVoid n){
        // label: mth_<methodName>_<className>
        code.emit("mth_" + n.name + "_" + n.classDecl.name + ":");
        
        // push $ra to make nested calls and still return correctly
        // set $fp = $sp so params are at positive offsets
        // locals will be at negative offsets 
        code.emit(n, " subu $sp, $sp, 4");
        code.emit(n, " sw $ra, 0($sp)");
        code.emit(n, " move $fp, $sp");

        // reset stack at start of each method
        stack = 0;

        // generate code for method body
        n.stmts.accept(this);

        // restore $sp to $fp
        // restore $ra, pop its slot, then return
        code.emit(n, "  move $sp, $fp");
        code.emit(n, "  lw   $ra, 0($sp)");
        code.emit(n, "  addu $sp, $sp, 4");
        code.emit(n, "  jr   $ra");
 
        return null;
    }

    // statements
    @Override
    public Object visit(LocalDeclStmt n){
        n.localVarDecl.accept(this);
        return null;
    }

    @Override
    public Object visit(LocalVarDecl n){
        n.initExp.accept(this);
        n.offset = -stack; // negative from $fp
        return null;
    }

    @Override
    public Object visit(CallStmt n){
        n.callExp.accept(this);
        return null;
    }

    // stack helpers
    private void push(String reg){
        // push a register value onto the stack and update stack counter
        code.emit(" subu $sp, $sp, 4");
        code.emit(" sw " + reg + ", 0($sp)");
        stack += 4;
    }

    private void pop(String reg){
        // pop a register value onto the stack and update stack counter
        code.emit(" lw " + reg + ", 0($sp)");
        code.emit(" addu $sp, $sp, 4");
        stack -= 4;
    }

    // expressions
    // integer literal
    @Override
    public Object visit(IntLit n){
        code.emit(n," li $t0, " + n.val);
        push("$t0");
        return null;
    }

    // boolean literal true: push 1
    @Override
    public Object visit(True n){
        code.emit(n, " li $t0, 1");
        push("$t0");
        return null;
    }

    // boolean literal false: push 0
    @Override
    public Object visit(False n){
        code.emit(n, " li $t0, 0");
        push("$t0");
        return null;
    }

    // string literal: push address of string object
    @Override
    public Object visit(StringLit n){
        StringLit primaryString;
        if(n.uniqueCgRep != null){
            // if a unique version exists, use it
            primaryString = n.uniqueCgRep;
        } else{ 
            primaryString = n;
        }
        code.emit(n, " la $t0, strLit_" + primaryString.pos);
        push("$t0");
        return null;
    }

    // variable reference: laod from its slot relative to $fp
    @Override
    public Object visit(IDExp n){
        int offset = n.link.offset;
        code.emit(n, " lw $t0, " + offset + "($fp)");
        push("$t0");
        return null;
    }

    // this: current object pointer always in $s2
    @Override
    public Object visit(This n){
        push("$s2");
        return null;
    }

    // super: current object pointer always in $s2
    @Override
    public Object visit(Super n){
        push("$s2");
        return null;
    }

    // arithmetic 
    // plus
    @Override
    public Object visit(Plus n){
       n.left.accept(this);
        n.right.accept(this);

        code.emit("  lw $t1, ($sp)");
        code.emit("  addu $sp, $sp, 4");
        code.emit("  lw $t0, ($sp)");
        code.emit("  addu $sp, $sp, 4");
        stack -= 8;

        code.emit("  add $t0, $t0, $t1");

        code.emit("  subu $sp, $sp, 4");
        code.emit("  sw $t0, ($sp)");
        stack += 4;

        return null;
    }

    // minus
    @Override
    public Object visit(Minus n){
        n.left.accept(this);
        n.right.accept(this);

        code.emit("  lw $t1, ($sp)");
        code.emit("  addu $sp, $sp, 4");
        code.emit("  lw $t0, ($sp)");
        code.emit("  addu $sp, $sp, 4");
        stack -= 8;

        code.emit("  sub $t0, $t0, $t1");

        code.emit("  subu $sp, $sp, 4");
        code.emit("  sw $t0, ($sp)");
        stack += 4;

        return null;
    }

    // multiply
    @Override
    public Object visit(Times n){
        n.left.accept(this);
        n.right.accept(this);

        code.emit("  lw $t1, ($sp)");
        code.emit("  addu $sp, $sp, 4");
        code.emit("  lw $t0, ($sp)");
        code.emit("  addu $sp, $sp, 4");
        stack -= 8;

        code.emit("  mul $t0, $t0, $t1");

        code.emit("  subu $sp, $sp, 4");
        code.emit("  sw $t0, ($sp)");
        stack += 4;

        return null;
    }

    // method calls
    @Override
    public Object visit(Call n){
        MethodDecl method = n.methodLink;
        push("$s2");
        n.args.accept(this);
        n.obj.accept(this); // pushes receiver
        pop("$s2"); // $s2 = receiver

        // call the method
        if (n.obj instanceof Super){
            MethodDecl targetMethod;
            if(method.superMethod != null){
                // using a method inherited from a parent class
                targetMethod = method.superMethod;
            } else{ // this class defined the method itself
                targetMethod = method;
            }

            // label for the MIPS jump
            String label = "mth_" + targetMethod.name + "_" + targetMethod.classDecl.name;
            
            // jal (jump and link) MIPS way to call a function
            code.emit(n, "  jal  " + label);
        } else{ 
            code.emit(n, " lw $t0, -12($s2)");
            code.emit(n, "  lw   $t0, " + (method.vtableOffset * 4) + "($t0)");
            code.emit(n, "  jalr $t0");
        }

        // after return, $sp points at the last argument
        // pop all arguments then restore $s2from old-this slot
        int argBytes = method.paramSize;
        if(argBytes > 0){
            code.emit(n, "  addu $sp, $sp, " + argBytes);
            stack -= argBytes;
        }
        // now old $s2 is at 0($sp)
        // restore and pop its slot.
        pop("$s2");

        return null;
    }

    @Override
    public Object visit(NewObject n){
        ClassDecl cls = n.objType.link;

        // tell the runtime how many words to allocate:
        // $s6 = number of primitive (int/bool) fields
        // $s7 = number of object-reference fields
        code.emit(n, "  li   $s6, " + cls.numDataFields);
        code.emit(n, "  li   $s7, " + cls.numObjFields);

        // newObject allocates memory, puts the pointer in $s7 and pushes it onto the stack
        code.emit(n, "  jal  newObject");

        // store the vtable pointer into the object's -12 header slot
        code.emit(n, "  la   $t0, CLASS_" + cls.name);
        code.emit(n, "  sw   $t0, -12($s7)");

        // newObject already pushed the object pointer onto the stack
        stack += 4;

        return null;
    }

    // assign

    // DeclList
    // StmtList

}
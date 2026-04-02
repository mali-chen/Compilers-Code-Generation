package visitor;

import syntaxtree.*;
import errorMsg.*;
import java.io.*;
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
        code.emit("  newObject");
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
        fakeMainStmt();

        //exit the program
        code.emit("  li $v0, 10");
        code.emit("  syscall");

        // This is a fake main method until you get MethodDeclVoid working.
        // When that's working you should remove these two lines.
        code.emit("mth_main_Main:");
        code.emit("  jr $ra");

        // emit code for all the methods in all the class declarations
        n.classDecls.accept(this);

        // flush the output and return
        code.flush();
        return null;
    }
}


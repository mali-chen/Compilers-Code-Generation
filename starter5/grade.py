# requires python 3.4 for pathlib
# python is on at least version 3.14, so this shouldn't be too hard

# This should be pretty easy to set up
# put this script and the test folder (unziped right ourside of your source directory
# Your directory structe needs to look like this.
# You can have more in it, but you need these files at a minimum.
# ├── grade.py
# ├── mips.jar
# ├── src
# │   ├── mjLib.asm
# │   ├── Lib.java
# │   ├── RunMain.java
# │   ├── errorMsg
# │   │   └── ...
# │   ├── main
# │   │   └── ...
# │   ├── parse
# │   │   └── ...
# │   ├── syntaxtree
# │   │   └── ...
# │   ├── visitor
# │   │   └── ...
# │   └── wrangLR.jar
# └── tests5a
#     └── batch0
#         └── ...
#
# to run the program you can use
# $ python3 grade.py tests5a

from sys import argv          # get the test directory
from pathlib import Path      # OS independent (hopefully) file handling
from contextlib import chdir  # change directories using a with block
from os import pathsep        # for compatability with windows since it uses ; for path seperators
from shutil import rmtree     # remove directory at the end
import subprocess             # just import subprocess, because the function is called run
                              # and I want my own version.
import shutil

# IF YOU ARE USING WINDOWS POWERSHELL YOU NEED TO CHANGE THIS TO TRUE!
# This is because windows powershell doesn't allow you to redirect stdin
# from a file (although, linux, mac, and windows cmd all do) because they hate me
# "ooh look at me, I'm windows powershell, and I can't do anything normail because
#  I'm build entirely about of bad decisions and incompetence."
POWERSHELL = False

# constants for the different scores
CRASH            = 0
MIPS_CRASH       = 1
DIFFERENT_OUTPUT = 2
CORRECT          = 3

# directories all be using
scrap = Path("scrap") # used to store java files.  This gets deleted at the end
src = Path("src")     # your source code directory
up = Path("..")
runMain = Path("RunMain.java") # The RunMain and Lib files
lib = Path("Lib.java")


def main():
    # I'm not checking anything here, you need to give me the right directory
    if len(argv) != 2:
        print("ERROR: just file name")
        print("USAGE: python3 grade.py tests")
        return
   
    # hold's the total score for each set of tests
    # for assignment 5a there's only one batch
    score = {}

    # scrap directory for running the java tests
    scrap.mkdir(exist_ok=True)
    shutil.copy(src / runMain, scrap / runMain)
    shutil.copy(src / lib, scrap / lib)
    #(src / lib).copy_into(scrap)
    #(src / lib).copy_into(scrap)

    # get each batch of tests and run them
    for batchDir in sorted(Path(argv[1]).iterdir()):
        batch = batchDir.name
        score[batch] = [0,0,0,0]

        # print the batch, so you know roughly how far through the tests you are.
        print(batch)
        for test in batchDir.iterdir():
            if test.suffix == ".java":
                # some tests have a .dat file that we'll need to handle reading use input
                dat = test.with_suffix(".java.dat")
                score[batch][runTest(test,dat)] += 1

    printScore(score)

    # we're done with scrap, kill it
    rmtree(scrap)

# Runs a single test that may or may not have a .dat file
# If we fail the test, print out the failure
# we return the score we got on the test
def runTest(test,dat):
    score, out, err, asm = mjCompile(test)

    # we crached running your compiler
    if not score:
        print(f"failed to compile {test}")
        print(f"stdout: {out}")
        print(f"stderr: {err}")
        return CRASH

    score, output, error = mips(asm, dat)

    # we ran the compiler, but the generated code couldn't be loaded into MIPS
    if not score:
        print(f"assembly failed to run for {test}")
        print(f"got error: {error}")
        return MIPS_CRASH

    java = javaRun(test, dat)

    # we ran the code, but got a different output than java
    if output != java:
        print(f"got different output for {test}")
        print(f"got:\n{output}\n")
        print(f"expected:\n{java}\n")
        return DIFFERENT_OUTPUT

    # everything worked, good job!
    return CORRECT

# Compile the code, returns
#  1. if the asm file was created successfully
#  2. the output of running out compiler
#  3. any exceptions that might have been thrown
#  4. the name of the asm file created
def mjCompile(test):
    with chdir("src"):
        (out,err) = run(f'java -cp ".{pathsep}wrangLR.jar" main.Main {up / test} -a mjLib.asm')
        asm = test.with_suffix(".asm")
        return ((up / asm).is_file(), out, err, asm)

# Runs the mips assembler on your code
# returns
# 1.  if the code ran to completion
#    If it did, then "successful" is printed out to stderr
# 2. the output of your code
# 3. any errors mips might have given you
def mips(test, dat):
    out = ""
    err = ""
    # if we have a .dat file, we need to feed that into the interpreter.
    # because windows powershell is stupid, we need to have a special case for it.
    if dat.is_file():
        if POWERSHELL:
            out,err = run(f'cat {dat} | java -cp ".{pathsep}mips.jar" mipsSim.MipsSim {test}')
        else:
            out,err = run(f'java -cp ".{pathsep}mips.jar" mipsSim.MipsSim {test} < {dat}')
    else:
        out,err = run(f'java -cp ".{pathsep}mips.jar" mipsSim.MipsSim {test}')
    return ("successful" in err, out, err)

# Runs java on the program and returns the output.
def javaRun(test,dat):
    out = ""
    shutil.copy(test, scrap / Path(test.name))
    #test.copy_into(scrap)

    with chdir(scrap):
        # actually compile the java code.
        run(f"javac *.java")

        out = ""
        err = ""

        # if the dat file exsists, then we need to feed it into the program
        if (up/dat).is_file():
            if POWERSHELL:
                out, err = run(f"cat {up/dat} | java RunMain")
            else:
                out, err = run(f"java RunMain < {up/dat}")
        else:    
            out, err = run("java RunMain")

        # delete the test.java file and all of the classes
        Path(test.name).unlink()
        #[p.unlink() for p in Path(".").glob("*.class")]
        map(lambda p: p.unlink(), Path(".").glob("*.class"))

        return out

# print out the score
def printScore(score):
    total = 0      # total points
    pts = 0        # points you've earned
    ec = 0         # extention points
    has_ec = False # if we have any extensions (5a doesn't)

    #print header
    print("--------------------------------------------------------------")
    print("| Batch        | CRASHED | MIPS CRASHED | MISMATCH | CORRECT |")
    print("|--------------+---------+--------------+----------+---------|")

    for batch in score:
        x = score[batch]
        print(f"| {batch:<12} | {x[0]:>7} | {x[1]:>12} | {x[2]:>8} | {x[3]:>7} |")

        # add weighted score to the points we've got so far
        # the total is assuming everything was correct
        if batch == "switchTests":
            if x[3] == sum(x):
                ec += 5
            has_ec = True
        elif batch == "strLitTests":
            if x[3] == sum(x):
                ec += 10
            has_ec = True
        else:
            pts += CRASH * x[0] + MIPS_CRASH * x[1] + DIFFERENT_OUTPUT * x[2] + CORRECT * x[3]
            total += CORRECT * sum(x)
    
    #print end with total score
    print("--------------------------------------------------------------")
    if ec != 0:
        print(f"\nTotal score: {int((90*pts + ec) / total)} / 100\n")
    else:
        print(f"\nTotal score: {int(100*pts / total)} / 100\n")


#runs a single command and returns stdout,stderr as a tuple
def run(cmd):
    result = subprocess.run(cmd, shell=True, capture_output=True)
    return result.stdout.decode(), result.stderr.decode()


if __name__ == '__main__':
    main()

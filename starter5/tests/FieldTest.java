
class Main
{
    public void main()
    {
    }
}

class A { int x; }
class B extends A {
    C c;
    boolean y;
}
class C extends B {
    int z;
    A a;
}
class D extends C {
    String s;
    B b;
    int w;
}
class E extends D {
    D d;
}

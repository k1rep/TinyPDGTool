package example.pdgextractor;

public class Example {
    private static final int CONSTANT = 42;

    public int sum(int a, int b) {
        return a + b;
    }

    public Example() {
        System.out.println("Hello World!");
    }

    public static void function() {
        Runnable r = () -> System.out.println("Hello World!");
        r.run();
    }

    public static void test(){
        Example e = new Example();
        int a = 0;
        int c = e.sum(1, 2);
        System.out.println(a+c);
        function();
    }
}

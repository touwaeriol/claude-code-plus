public class TestClass {
    private String name;
    private int age;
    
    public TestClass() {
        this.name = "Default";
        this.age = 0;
    }
    
    public TestClass(String name, int age) {
        this.name = name;
        this.age = age;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public int getAge() {
        return age;
    }
    
    public void setAge(int age) {
        this.age = age;
    }
    
    @Override
    public String toString() {
        return "TestClass{" +
                "name='" + name + '\'' +
                ", age=" + age +
                '}';
    }
    
    public static void main(String[] args) {
        TestClass test = new TestClass("Alice", 25);
        System.out.println(test);
    }
}

public class User {
    private int id;
    private String name;
    private Environment env;

    public User(int id, String name, Environment env)
    {
        this.id = id;
        this.name = name;
        this.env = env;
    }

    public int getId()
    {
        return this.id;
    }

    public String getName()
    {
        return this.name;
    }

    public Environment getEnv()
    {
        return this.env;
    }
}

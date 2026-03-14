public class Utils {
    public static Environment getEnv(String env)
    {
        switch(env)
        {
            case "DEV":
                return Environment.DEV;
            case "STAGING":
                return Environment.STAGING;
            default:
                return Environment.PROD;
        }
    }
}

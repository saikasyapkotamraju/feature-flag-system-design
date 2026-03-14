import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class UserService {
    private static Set<User> userList = new HashSet<>();

    public static User addUser(String name, String env)
    {
        Environment userEnv = Utils.getEnv(env);
        User newUser = new User(ThreadLocalRandom.current().nextInt(1000), name, userEnv);
        userList.add(newUser);
        return newUser;
    }

    public static boolean removeUser(String name, String env)
    {
        Environment userEnv = Utils.getEnv(env);
        User targetUser = null;
        for(User user : userList)
        {
            if(user.getName().equalsIgnoreCase(name) && user.getEnv()==userEnv)
            {
                targetUser = user;
                break;
            }
        }
        userList.remove(targetUser);
        return true;
    }

    public static User getUser(int userId)
    {
        for(User user : userList)
        {
            if(user.getId() == userId)
            {
                return user;
            }
        }
        return null;
    }

    public static boolean enabledFeatureForUser(String name, String env, String featureName)
    {
        User targetUser = null;
        Environment userEnv = Utils.getEnv(env);
        for(User user : userList)
        {
            if(user.getName().equalsIgnoreCase(name) && user.getEnv()==userEnv)
            {
                targetUser = user;
                break;
            }
        }
        return FeatureFlagService.addUserToFeature(targetUser, featureName);
    }
}

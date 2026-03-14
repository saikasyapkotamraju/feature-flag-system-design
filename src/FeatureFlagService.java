import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public class FeatureFlagService {

    private static FeatureFlagStoreImpl featureFlagStore = new FeatureFlagStoreImpl();

    public static boolean addFeatureFlag(String name, String env, int rollout, boolean enabled)
    {
        FeatureFlag featureFlag = new FeatureFlag(ThreadLocalRandom.current().nextInt(1000), name, enabled, rollout);
        featureFlag.addEnabledEnv(Utils.getEnv(env));
        return featureFlagStore.addFlag(featureFlag);
    }

    public static boolean removeFeatureFlag(String name)
    {
        return featureFlagStore.removeFlag(name);
    }

    public static boolean isEnabled(User user, String featureFlagName)
    {
        FeatureFlag flag = featureFlagStore.getFlag(featureFlagName);
        if(flag == null)
        {
            return false;
        }

        if(!flag.isEnabled())
        {
            return false;
        }

        int userId = user.getId();
        if(!flag.getEnabledUsers().isEmpty() && flag.getEnabledUsers().contains(userId))
        {
            return true;
        }

        Environment userEnv = user.getEnv();
        if(!flag.getEnabledEnvs().contains(userEnv))
        {
            return false;
        }

        int rolloutBucket = computeRolloutBucket(user.getId(), featureFlagName);
//        System.out.println("User rollout bucket :" + rolloutBucket);
        return rolloutBucket < flag.getRolloutPercentage();

    }

    public static int computeRolloutBucket(int userId, String flagName)
    {
        int bucket = Math.abs((userId + flagName).hashCode());
        return bucket % 100;
    }

    public static FeatureFlag getFeatureFlag(String flagName)
    {
        return featureFlagStore.getFlag(flagName);
    }

    public static boolean addUserToFeature(User user, String featureName)
    {
        FeatureFlag featureFlag = getFeatureFlag(featureName);
        featureFlag.addEnabledUser(user.getId());
        return true;
    }

    public static void disableFeatureFlag(String featureName)
    {
        FeatureFlag flag = featureFlagStore.getFlag(featureName);
        flag.disableFlag();
    }

    public static void enableFeatureFlag(String featureName)
    {
        FeatureFlag flag = featureFlagStore.getFlag(featureName);
        flag.enableFlag();
    }
}

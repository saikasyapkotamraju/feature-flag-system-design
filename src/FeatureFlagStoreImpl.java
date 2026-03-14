import java.util.*;

public class FeatureFlagStoreImpl implements FeatureFlagStore{
    private Map<String, FeatureFlag> featureFlagMap = new HashMap<>();

    public FeatureFlagStoreImpl()
    {

    }

    public boolean addFlag(FeatureFlag flag)
    {
        if(featureFlagMap == null)
        {
            featureFlagMap = new HashMap<>();
        }
        featureFlagMap.put(flag.getName(), flag);
        return true;
    }
    public FeatureFlag getFlag(String name)
    {
        if(featureFlagMap==null)
        {
            System.out.println("Feature flag store not initiated!");
            return null;
        }
        if(!featureFlagMap.containsKey(name))
        {
            System.out.println("Feature flag not present!");
            return null;
        }
        return featureFlagMap.get(name);
    }

    public boolean removeFlag(String name)
    {
        if(featureFlagMap!=null)
        {
            featureFlagMap.remove(name);
        }
        return true;
    }
}

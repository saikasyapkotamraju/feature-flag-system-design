import java.util.*;
public class FeatureFlag {
    private int id;
    private String name;
    private boolean enabled;
    private Set<Environment> enabledEnvs;
    private int rolloutPercentage;
    private Set<Integer> enabledUsers;

    public FeatureFlag(int id, String name, boolean enabled, int rollout)
    {
        this.id = id;
        this.name = name;
        this.enabled = enabled;
        this.rolloutPercentage = rollout;
        enabledEnvs = new HashSet<>();
        enabledUsers = new HashSet<>();
    }

    public boolean addEnabledEnv(Environment env)
    {
        if(enabledEnvs == null)
        {
            enabledEnvs = new HashSet<>();
        }
        enabledEnvs.add(env);
        return true;
    }

    public boolean removeEnabledEnv(Environment env)
    {
        if(enabledEnvs!=null)
        {
            enabledEnvs.remove(env);
        }
        return true;
    }

    public boolean addEnabledUser(int userId)
    {
        if(enabledUsers==null)
        {
            enabledUsers = new HashSet<>();
        }
        enabledUsers.add(userId);
        return true;
    }

    public boolean removeEnabledUser(int userId)
    {
        if(enabledUsers==null)return true;
        enabledUsers.remove(userId);
        return true;
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Set<Environment> getEnabledEnvs() {
        if (enabledEnvs == null) {
            enabledEnvs = new HashSet<>();
        }
        return enabledEnvs;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public Set<Integer> getEnabledUsers() {
        if (enabledUsers == null) {
            enabledUsers = new HashSet<>();
        }
        return enabledUsers;
    }

    public void disableFlag()
    {
        this.enabled = false;
    }

    public void enableFlag()
    {
        this.enabled = true;
    }

}

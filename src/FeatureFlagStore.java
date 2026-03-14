public interface FeatureFlagStore {
    public FeatureFlag getFlag(String name);
    public boolean removeFlag(String name);
}

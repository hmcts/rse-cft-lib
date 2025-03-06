package uk.gov.hmcts.reform.roleassignment.config;

import com.launchdarkly.sdk.EvaluationDetail;
import com.launchdarkly.sdk.LDUser;
import com.launchdarkly.sdk.LDValue;
import com.launchdarkly.sdk.server.FeatureFlagsState;
import com.launchdarkly.sdk.server.FlagsStateOption;
import com.launchdarkly.sdk.server.interfaces.BigSegmentStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataSourceStatusProvider;
import com.launchdarkly.sdk.server.interfaces.DataStoreStatusProvider;
import com.launchdarkly.sdk.server.interfaces.FlagTracker;
import com.launchdarkly.sdk.server.interfaces.LDClientInterface;

import java.io.IOException;

public class LDDummyClient implements LDClientInterface {
    @Override
    public boolean isInitialized() {
        return false;
    }

    @Override
    public void track(String eventName, LDUser user) {

    }

    @Override
    public void trackData(String eventName, LDUser user, LDValue data) {

    }

    @Override
    public void trackMetric(String eventName, LDUser user, LDValue data, double metricValue) {

    }

    @Override
    public void identify(LDUser user) {

    }

    @Override
    public FeatureFlagsState allFlagsState(LDUser user, FlagsStateOption... options) {
        return null;
    }

    @Override
    public boolean boolVariation(String featureKey, LDUser user, boolean defaultValue) {
        return true;
    }

    @Override
    public int intVariation(String featureKey, LDUser user, int defaultValue) {
        return 0;
    }

    @Override
    public double doubleVariation(String featureKey, LDUser user, double defaultValue) {
        return 0;
    }

    @Override
    public String stringVariation(String featureKey, LDUser user, String defaultValue) {
        return null;
    }

    @Override
    public LDValue jsonValueVariation(String featureKey, LDUser user, LDValue defaultValue) {
        return null;
    }

    @Override
    public EvaluationDetail<Boolean> boolVariationDetail(String featureKey, LDUser user, boolean defaultValue) {
        return null;
    }

    @Override
    public EvaluationDetail<Integer> intVariationDetail(String featureKey, LDUser user, int defaultValue) {
        return null;
    }

    @Override
    public EvaluationDetail<Double> doubleVariationDetail(String featureKey, LDUser user, double defaultValue) {
        return null;
    }

    @Override
    public EvaluationDetail<String> stringVariationDetail(String featureKey, LDUser user, String defaultValue) {
        return null;
    }

    @Override
    public EvaluationDetail<LDValue> jsonValueVariationDetail(String featureKey, LDUser user, LDValue defaultValue) {
        return null;
    }

    @Override
    public boolean isFlagKnown(String featureKey) {
        return true;
    }

    @Override
    public void close() throws IOException {

    }

    @Override
    public void flush() {

    }

    @Override
    public boolean isOffline() {
        return false;
    }

    @Override
    public FlagTracker getFlagTracker() {
        return null;
    }

    @Override
    public BigSegmentStoreStatusProvider getBigSegmentStoreStatusProvider() {
        return null;
    }

    @Override
    public DataSourceStatusProvider getDataSourceStatusProvider() {
        return null;
    }

    @Override
    public DataStoreStatusProvider getDataStoreStatusProvider() {
        return null;
    }

    @Override
    public String secureModeHash(LDUser user) {
        return null;
    }

    @Override
    public void alias(LDUser user, LDUser previousUser) {

    }

    @Override
    public String version() {
        return null;
    }
}

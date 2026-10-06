package com.apexstore.common;

import com.zeroc.Ice.Communicator;
import com.zeroc.Ice.InitializationData;
import com.zeroc.Ice.Properties;
import com.zeroc.Ice.Util;

import java.io.IOException;
import java.io.InputStream;


public final class IceConfig {

    private IceConfig() {
    }

    public static Communicator init(String[] args, String cfgFile) {

        Properties props = Util.createProperties(args);

        try (InputStream in = IceConfig.class.getResourceAsStream("/" + cfgFile)) {

            if (in == null) {
                throw new IllegalStateException(
                        cfgFile + " was not found on the classpath.");
            }

            java.util.Properties base = new java.util.Properties();
            base.load(in);

            for (String key : base.stringPropertyNames()) {
                if (props.getProperty(key).isEmpty()) {
                    props.setProperty(key, base.getProperty(key).trim());
                }
            }

        } catch (IOException e) {
            throw new IllegalStateException("Error reading " + cfgFile, e);
        }

        InitializationData data = new InitializationData();
        data.properties = props;
        return Util.initialize(data);
    }

    public static int intValue(Communicator c, String key, int defaultValue) {
        return c.getProperties().getPropertyAsIntWithDefault(key, defaultValue);
    }
}

/*
 * ------------------------------------------------------------------------------------------------
 * Copyright 2014 by Swiss Post, Information Technology Services
 * ------------------------------------------------------------------------------------------------
 * $Id$
 * ------------------------------------------------------------------------------------------------
 */

package org.swisspush.reststorage.lua;

import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.swisspush.reststorage.JedisFactory;
import org.swisspush.reststorage.util.LockMode;
import redis.clients.jedis.Jedis;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.UUID;

/**
 * Abstract class containing common methods for LuaScript tests
 */
@RunWith(VertxUnitRunner.class)
public abstract class AbstractLuaScriptTest {

    final static String prefixResources = "rest-storage:resources";
    final static String prefixCollections = "rest-storage:collections";
    final static String expirableSet = "rest-storage:expirable";
    final static String prefixDeltaResources = "delta:resources";
    final static String prefixDeltaEtags = "delta:etags";
    final static String prefixLock = "redis-storage:locks";

    static final String MAX_EXPIRE = "9999999999999";

    Jedis jedis = null;

    @Before
    public void connect() {
        jedis = JedisFactory.createJedis();
    }

    @After
    public void disconnect() {
        jedis.flushAll();
        jedis.close();
    }

    protected double getNowAsDouble() {
        return (double) System.currentTimeMillis();
    }

    protected String getNowAsString() {
        return String.valueOf(System.currentTimeMillis());
    }

    protected String readScript(String scriptFileName) {
        return readScript(scriptFileName, false);
    }

    protected String readScript(String scriptFileName, boolean stripLogNotice) {
        BufferedReader in = new BufferedReader(new InputStreamReader(this.getClass().getClassLoader().getResourceAsStream(scriptFileName)));
        StringBuilder sb;
        try {
            sb = new StringBuilder();
            String line;
            while ((line = in.readLine()) != null) {
                if (stripLogNotice && line.contains("redis.LOG_NOTICE,")) {
                    continue;
                }
                sb.append(line + "\n");
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        } finally {
            try {
                in.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        return sb.toString();
    }

    protected String evalScriptPut(final String resourceName, final String resourceValue) {
        return evalScriptPut(resourceName, resourceValue, MAX_EXPIRE);
    }

    protected String evalScriptPut(final String resourceName, final String resourceValue, final String expire) {
        return evalScriptPut(resourceName, resourceValue, expire, UUID.randomUUID().toString());
    }

    protected String evalScriptPut(final String resourceName, final String resourceValue, final String expire, final String etag) {
        return evalScriptPut(resourceName, resourceValue, expire, etag, "", LockMode.SILENT, 300);
    }

    protected String evalScriptPut(final String resourceName, final String resourceValue, final String expire, final String etag, boolean storeCompressed) {
        return evalScriptPut(resourceName, resourceValue, expire, etag, "", LockMode.SILENT, 300, storeCompressed);
    }

    protected String evalScriptPut(final String resourceName, final String resourceValue, final String expire, final String etag, final String lockOwner, final LockMode lockMode, final long lockExpire) {
        return evalScriptPut(resourceName, resourceValue, expire, etag, lockOwner, lockMode, lockExpire, false);
    }

    @SuppressWarnings({"rawtypes", "unchecked", "serial"})
    protected String evalScriptPut(final String resourceName, final String resourceValue, final String expire, final String etag, final String lockOwner, final LockMode lockMode, final long lockExpire, boolean storeCompressed) {
        String putScript = readScript("put.lua");
        String etagTmp;
        if (etag != null && !etag.isEmpty()) {
            etagTmp = etag;
        } else {
            etagTmp = UUID.randomUUID().toString();
        }

        String lockExpireInMillis = String.valueOf(System.currentTimeMillis() + (lockExpire * 1000));

        final String etagValue = etagTmp;
        return (String) jedis.eval(putScript, new ArrayList() {
                    {
                        add(resourceName);
                    }
                }, new ArrayList() {
                    {
                        add(prefixResources);
                        add(prefixCollections);
                        add(expirableSet);
                        add("false");
                        add(expire);
                        add("9999999999999");
                        add(resourceValue);
                        add(etagValue);
                        add(prefixLock);
                        add(lockOwner);
                        add(lockMode.text());
                        add(lockExpireInMillis);
                        add(storeCompressed ? "1" : "0");
                    }
                }
        );
    }

    protected Object evalScriptGet(final String resourceName) {
        return evalScriptGet(resourceName, String.valueOf(System.currentTimeMillis()));
    }

    protected Object evalScriptGet(final String resourceName, final String timestamp) {
        return evalScriptGet(resourceName, timestamp, "", "");
    }

    protected Object evalScriptGetOffsetCount(final String resourceName1, final String offset, final String count) {
        return evalScriptGet(resourceName1, String.valueOf(System.currentTimeMillis()), offset, count);
    }

    @SuppressWarnings({ "rawtypes", "unchecked", "serial" })
    protected Object evalScriptGet(final String resourceName, final String timestamp, final String offset, final String count) {
        String getScript = readScript("get.lua");
        return jedis.eval(getScript, new ArrayList() {
                    {
                        add(resourceName);
                    }
                }, new ArrayList() {
                    {
                        add(prefixResources);
                        add(prefixCollections);
                        add(expirableSet);
                        add(timestamp);
                        add("9999999999999");
                        add(offset);
                        add(count);
                    }
                }
        );
    }
}

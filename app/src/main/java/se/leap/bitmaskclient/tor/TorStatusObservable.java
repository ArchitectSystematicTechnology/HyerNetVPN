package se.leap.bitmaskclient.tor;
/**
 * Copyright (c) 2021 LEAP Encryption Access Project and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import static se.leap.bitmaskclient.tor.TorStatusObservable.SnowflakeStatus.BROKER_REPLIED_SUCCESS;
import static se.leap.bitmaskclient.tor.TorStatusObservable.SnowflakeStatus.NEGOTIATING_RENDEZVOUS_VIA_AMP_CACHE;
import static se.leap.bitmaskclient.tor.TorStatusObservable.SnowflakeStatus.NEGOTIATING_RENDEZVOUS_VIA_HTTP;
import static se.leap.bitmaskclient.tor.TorStatusObservable.SnowflakeStatus.RETRY_AMP_CACHE_RENDEZVOUS;
import static se.leap.bitmaskclient.tor.TorStatusObservable.SnowflakeStatus.RETRY_HTTP_RENDEZVOUS;
import static se.leap.bitmaskclient.tor.TorStatusObservable.SnowflakeStatus.STARTED;
import static se.leap.bitmaskclient.tor.TorStatusObservable.SnowflakeStatus.STOPPED;
import static se.leap.bitmaskclient.tor.TorStatusObservable.TorStatus.ON;

import android.content.Context;
import android.util.Log;

import androidx.annotation.Nullable;

import java.util.Observable;
import java.util.Observer;
import java.util.Vector;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

import se.leap.bitmaskclient.R;
import se.leap.bitmaskclient.base.utils.PreferenceHelper;

public class TorStatusObservable extends Observable {

    private static final String TAG = TorStatusObservable.class.getSimpleName();

    public interface StatusCondition {
        boolean met();
    }

    public enum TorStatus {
        ON,
        OFF,
        STARTING,
        STOPPING
    }

    public enum SnowflakeStatus {
        STARTED,
        NEGOTIATING_RENDEZVOUS_VIA_HTTP,
        NEGOTIATING_RENDEZVOUS_VIA_AMP_CACHE,
        RETRY_HTTP_RENDEZVOUS,
        RETRY_AMP_CACHE_RENDEZVOUS,
        BROKER_REPLIED_SUCCESS,
        STOPPED
    }

    // indicates if the user has cancelled Tor, the actual TorStatus can still be different until
    // the TorService has sent the shutdown signal
    private boolean cancelled = false;

    public static final String LOG_TAG_TOR = "[TOR]";
    public static final String LOG_TAG_SNOWFLAKE = "[SNOWFLAKE]";
    public static final String SNOWFLAKE_STARTED = "--- Starting Snowflake Client ---";
    public static final String SNOWFLAKE_STOPPED_COLLECTING = "---- SnowflakeConn: end collecting snowflakes ---";
    public static final String SNOWFLAKE_COPY_LOOP_STOPPED = "copy loop ended";
    public static final String SNOWFLAKE_SOCKS_ERROR = "SOCKS accept error";
    public static final String SNOWFLAKE_NEGOTIATING_HTTP = "Negotiating via HTTP rendezvous...";
    public static final String SNOWFLAKE_NEGOTIATING_AMP_CACHE = "Negotiating via AMP cache rendezvous...";
    public static final String SNOWFLAKE_CONNECTION_CLOSING = "WebRTC: Closing";
    public static final String SNOWFLAKE_HTTP_RESPONSE_200 = "HTTP rendezvous response: 200";
    public static final String SNOWFLAKE_AMP_CACHE_RESPONSE_200 = "AMP cache rendezvous response: 200";

    private static TorStatusObservable instance;
    private TorStatus status = TorStatus.OFF;
    private SnowflakeStatus snowflakeStatus = STOPPED;
    private final TorNotificationManager torNotificationManager;
    private String lastError;
    private String lastTorLog = "";
    private String lastSnowflakeLog = "";
    private int httpProxyPort = -1;
    private int socksProxyPort = -1;
    private int bootstrapPercent = -1;
    private int retrySnowflakeRendezVous = 0;
    private final Vector<String> lastLogs = new Vector<>(100);

    private TorStatusObservable() {
        torNotificationManager = new TorNotificationManager();
    }

    public static TorStatusObservable getInstance() {
        if (instance == null) {
            instance = new TorStatusObservable();
        }
        return instance;
    }

    public static TorStatus getStatus() {
        return getInstance().status;
    }

    public static SnowflakeStatus getSnowflakeStatus() {
        return getInstance().snowflakeStatus;
    }

    /**
     * Waits on the current Thread until a certain tor/snowflake status has been reached
     * @param condition defines when wait should be interrupted
     * @param timeout Timout in seconds
     * @throws InterruptedException if thread was interrupted while waiting
     * @throws TimeoutException thrown if timeout was reached
     * @return true return value only needed to mock this method call
     */
    public static boolean waitUntil(StatusCondition condition, int timeout) throws InterruptedException, TimeoutException {
        CountDownLatch countDownLatch = new CountDownLatch(1);
        final AtomicBoolean conditionMet = new AtomicBoolean(false);
        Observer observer = (o, arg) -> {
              if (condition.met()) {
                  countDownLatch.countDown();
                  conditionMet.set(true);
              }
        };
        if (condition.met()) {
            // no need to wait
            return true;
        }
        getInstance().addObserver(observer);
        countDownLatch.await(timeout, TimeUnit.SECONDS);
        getInstance().deleteObserver(observer);
        if (!conditionMet.get()) {
            throw new TimeoutException("Status condition not met within " + timeout + "s.");
        }
        return true;
    }

    public static void logSnowflakeMessage(Context context, String message) {
        addLog(message);
        getInstance().lastSnowflakeLog = message;
        if (getInstance().status != TorStatus.OFF) {
            getInstance().torNotificationManager.buildTorNotification(context, getStringForCurrentStatus(context), getNotificationLog(context), getBootstrapProgress(), false);
        }
        //TODO: implement proper state signalling in IPtProxy
        message = message.trim();
        if (SNOWFLAKE_STARTED.equals(message)) {
            getInstance().snowflakeStatus = STARTED;
        } else if (SNOWFLAKE_NEGOTIATING_HTTP.equals(message)) {
            getInstance().snowflakeStatus = NEGOTIATING_RENDEZVOUS_VIA_HTTP;
        } else if (SNOWFLAKE_NEGOTIATING_AMP_CACHE.equals(message)) {
            getInstance().snowflakeStatus = NEGOTIATING_RENDEZVOUS_VIA_AMP_CACHE;
        } else if (SNOWFLAKE_STOPPED_COLLECTING.equals(message) ||
                SNOWFLAKE_COPY_LOOP_STOPPED.equals(message) ||
                message.contains(SNOWFLAKE_SOCKS_ERROR)) {
            getInstance().snowflakeStatus = STOPPED;
        } else if (SNOWFLAKE_CONNECTION_CLOSING.equals(message)) {
            if (getInstance().snowflakeStatus == NEGOTIATING_RENDEZVOUS_VIA_HTTP) {
                if (getInstance().retrySnowflakeRendezVous < 3) {
                    getInstance().retrySnowflakeRendezVous += 1;
                } else {
                    getInstance().retrySnowflakeRendezVous = 0;
                    getInstance().snowflakeStatus = RETRY_AMP_CACHE_RENDEZVOUS;
                }
            } else if (getInstance().snowflakeStatus == NEGOTIATING_RENDEZVOUS_VIA_AMP_CACHE) {
                if (getInstance().retrySnowflakeRendezVous < 3) {
                    getInstance().retrySnowflakeRendezVous += 1;
                } else {
                    getInstance().retrySnowflakeRendezVous = 0;
                    getInstance().snowflakeStatus = RETRY_HTTP_RENDEZVOUS;
                }
            }
        } else if (SNOWFLAKE_AMP_CACHE_RESPONSE_200.equals(message) || SNOWFLAKE_HTTP_RESPONSE_200.equals(message)) {
            getInstance().snowflakeStatus = BROKER_REPLIED_SUCCESS;
            getInstance().retrySnowflakeRendezVous = 0;
        }
        Log.d(TAG, "snowflake status " + getInstance().snowflakeStatus);
        instance.setChanged();
        instance.notifyObservers();
    }

    private static String getNotificationLog(Context context) {
        String notificationLog = "Tor: " + getInstance().lastTorLog;
        if (PreferenceHelper.getUseSnowflake(context)) {
            String snowflakeIcon = new String(Character.toChars(0x2744));
            String snowflakeLog = getInstance().lastSnowflakeLog;
            // we don't want to show the response json in the notification
            if (snowflakeLog != null && snowflakeLog.contains("Received answer: {")) {
                snowflakeLog = "Received Answer.";
            }
            notificationLog += "\n" + snowflakeIcon + ": " + snowflakeLog;
        }
        return notificationLog;
    }

    public static int getBootstrapProgress() {
        return getInstance().status == TorStatus.STARTING ? getInstance().bootstrapPercent : -1;
    }

    private static void addLog(String message) {
        if (instance.lastLogs.size() > 100) {
            instance.lastLogs.remove(99);
        }
        instance.lastLogs.add(0, message.trim());
    }

    public static void updateState(Context context, String status) {
        updateState(context,status, -1, null);
    }

    public static void updateState(Context context, String status, int bootstrapPercent, @Nullable String logKey) {
        try {
            Log.d(TAG, "update tor state: " + status + " " + bootstrapPercent + " "+ logKey);
            getInstance().status = TorStatus.valueOf(status);
            if (bootstrapPercent != -1) {
                getInstance().bootstrapPercent = bootstrapPercent;
            }

            if (getInstance().status == TorStatus.OFF) {
                getInstance().torNotificationManager.cancelNotifications(context);
                getInstance().cancelled = false;
            } else {
                if (logKey != null) {
                    getInstance().lastTorLog = getStringFor(context, logKey);
                    addLog(getInstance().lastTorLog);
                }
                // after bootstrapping was successful, TorService sends and status ON update without any bootstrapping or logKey value
                boolean forceShowingAfterBootstrapping = getInstance().status == ON && bootstrapPercent == -1 && logKey == null;
                getInstance().torNotificationManager.buildTorNotification(context, getStringForCurrentStatus(context), getNotificationLog(context), getBootstrapProgress(), forceShowingAfterBootstrapping);
            }

            instance.setChanged();
            instance.notifyObservers();

        } catch (IllegalStateException e) {
            e.printStackTrace();
        }
    }

    private static String getStringFor(Context context, String key) {
        switch (key) {
            case "conn_pt":
                return context.getString(R.string.log_conn_pt);
            case "conn_done_pt":
                return context.getString(R.string.log_conn_done_pt);
            case "conn_done":
                return context.getString(R.string.log_conn_done);
            case "handshake":
                return context.getString(R.string.log_handshake);
            case "handshake_done":
                return context.getString(R.string.log_handshake_done);
            case "onehop_create":
                return context.getString(R.string.log_onehop_create);
            case "requesting_status":
                return context.getString(R.string.log_requesting_status);
            case "loading_status":
                return context.getString(R.string.log_loading_status);
            case "loading_keys":
                return context.getString(R.string.log_loading_keys);
            case "requesting_descriptors":
                return context.getString(R.string.log_requesting_descriptors);
            case "loading_descriptors":
                return context.getString(R.string.log_loading_descriptors);
            case "enough_dirinfo":
                return context.getString(R.string.log_enough_dirinfo);
            case "ap_handshake_done":
                return context.getString(R.string.log_ap_handshake_done);
            case "circuit_create":
                return context.getString(R.string.log_circuit_create);
            case "done":
                return context.getString(R.string.log_done);
            default:
                return key;
        }
    }

    public static void setLastError(String error) {
        getInstance().lastError = error;
        instance.setChanged();
        instance.notifyObservers();
    }

    public static void setProxyPorts(int httpProxyPort, int socksProxyPort) {
        getInstance().httpProxyPort = httpProxyPort;
        getInstance().socksProxyPort = socksProxyPort;
        instance.setChanged();
        instance.notifyObservers();
    }

    public static void setHttpProxyPort(int port) {
        getInstance().httpProxyPort = port;
        instance.setChanged();
        instance.notifyObservers();
    }

    public static int getHttpProxyPort() {
        return getInstance().httpProxyPort;
    }

    public static void setSocksProxyPort(int port) {
        getInstance().socksProxyPort = port;
        instance.setChanged();
        instance.notifyObservers();
    }

    public static int getSocksProxyPort() {
        return getInstance().socksProxyPort;
    }

    public static void waitForTorCircuits() throws InterruptedException, TimeoutException {
        if (TorStatusObservable.getStatus() == ON) {
            return;
        }
        TorStatusObservable.waitUntil(TorStatusObservable::isTorOnOrCancelled, 180);
    }

    private static boolean isTorOnOrCancelled() {
        return TorStatusObservable.getStatus() == ON || TorStatusObservable.isCancelled();
    }

    @Nullable
    public static String getLastTorLog() {
        return getInstance().lastTorLog;
    }

    @Nullable
    public static String getLastSnowflakeLog() {
        return getInstance().lastSnowflakeLog;
    }

    public static Vector<String> getLastLogs() {
        return getInstance().lastLogs;
    }

    public static String getStringForCurrentStatus(Context context) {
        if (context == null) {
            return "";
        }

        switch (getInstance().status) {
            case ON:
                return context.getString(R.string.tor_started);
            case STARTING:
                return context.getString(R.string.tor_starting);
            case STOPPING:
                return context.getString(R.string.tor_stopping);
            case OFF:
                break;
        }
        return "";
    }

    public static void markCancelled() {
        if (!getInstance().cancelled) {
            getInstance().cancelled = true;
            getInstance().httpProxyPort = -1;
            getInstance().socksProxyPort = -1;
            getInstance().setChanged();
            getInstance().notifyObservers();
        }
    }

    public static boolean isCancelled() {
        return getInstance().cancelled;
    }

    public static boolean isRunning() {
        return !TorStatusObservable.isCancelled() &&
                TorStatusObservable.getStatus() != TorStatusObservable.TorStatus.OFF;
    }
}

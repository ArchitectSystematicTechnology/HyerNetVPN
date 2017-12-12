/**
 * Copyright (c) 2013 LEAP Encryption Access Project and contributers
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
package se.leap.bitmaskclient.eip;

/**
 * Constants for intent passing, shared preferences
 *
 * @author Parménides GV <parmegv@sdf.org>
 */
public interface Constants {

    public final static String TAG = Constants.class.getSimpleName();

    public final static String ACTION_CHECK_CERT_VALIDITY = TAG + ".CHECK_CERT_VALIDITY";
    public final static String ACTION_START_EIP = TAG + ".START_EIP";
    public final static String ACTION_START_ALWAYS_ON_EIP = TAG + ".START_ALWAYS_ON_EIP";
    public final static String ACTION_STOP_EIP = TAG + ".STOP_EIP";
    public final static String ACTION_UPDATE_EIP_SERVICE = TAG + ".UPDATE_EIP_SERVICE";
    public final static String ACTION_IS_EIP_RUNNING = TAG + ".IS_RUNNING";
    public final static String ACTION_START_BLOCKING_VPN = TAG + ".ACTION_START_BLOCKING_VPN";
    public final static String ACTION_STOP_BLOCKING_VPN = TAG + ".ACTION_STOP_BLOCKING_VPN";
    public final static String EIP_NOTIFICATION = TAG + ".EIP_NOTIFICATION";
    public final static String ALLOWED_ANON = "allow_anonymous";
    public final static String ALLOWED_REGISTERED = "allow_registration";
    public final static String VPN_CERTIFICATE = "cert";
    public final static String PRIVATE_KEY = TAG + ".PRIVATE_KEY";
    public final static String KEY = TAG + ".KEY";
    public final static String RECEIVER_TAG = TAG + ".RECEIVER_TAG";
    public final static String REQUEST_TAG = TAG + ".REQUEST_TAG";
    public final static String PROVIDER_CONFIGURED = TAG + ".PROVIDER_CONFIGURED";
    public final static String IS_ALWAYS_ON = TAG + ".IS_ALWAYS_ON";
    public final static String RESTART_ON_BOOT = TAG + ".RESTART_ON_BOOT";

}

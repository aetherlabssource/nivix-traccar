/*
 * Copyright 2016 - 2020 Anton Tananaev (anton@traccar.org)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.traccar.model;

import org.traccar.storage.StorageName;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import java.util.HexFormat;

@StorageName("tc_events")
public class Event extends Message {

    public Event(String type, Position position) {
        setType(type);
        setPositionId(position.getId());
        setDeviceId(position.getDeviceId());
        eventTime = position.getDeviceTime();
    }

    public Event(String type, long deviceId) {
        setType(type);
        setDeviceId(deviceId);
        eventTime = new Date();
    }

    public Event() {}

    public static final String ALL_EVENTS = "allEvents";

    public static final String TYPE_COMMAND_RESULT = "commandResult";

    public static final String TYPE_DEVICE_ONLINE = "deviceOnline";
    public static final String TYPE_DEVICE_UNKNOWN = "deviceUnknown";
    public static final String TYPE_DEVICE_OFFLINE = "deviceOffline";
    public static final String TYPE_DEVICE_INACTIVE = "deviceInactive";
    public static final String TYPE_QUEUED_COMMAND_SENT = "queuedCommandSent";

    public static final String TYPE_DEVICE_MOVING = "deviceMoving";
    public static final String TYPE_DEVICE_STOPPED = "deviceStopped";

    public static final String TYPE_DEVICE_OVERSPEED = "deviceOverspeed";
    public static final String TYPE_DEVICE_FUEL_DROP = "deviceFuelDrop";
    public static final String TYPE_DEVICE_FUEL_INCREASE = "deviceFuelIncrease";

    public static final String TYPE_GEOFENCE_ENTER = "geofenceEnter";
    public static final String TYPE_GEOFENCE_EXIT = "geofenceExit";

    public static final String TYPE_PROXIMITY_ENTER = "proximityEnter";
    public static final String TYPE_PROXIMITY_EXIT = "proximityExit";
    public static final String TYPE_UNACCOMPANIED_MOTION = "unaccompaniedMotion";

    public static final String TYPE_ALARM = "alarm";

    public static final String TYPE_IGNITION_ON = "ignitionOn";
    public static final String TYPE_IGNITION_OFF = "ignitionOff";

    public static final String TYPE_MAINTENANCE = "maintenance";
    public static final String TYPE_DRIVER_CHANGED = "driverChanged";
    public static final String TYPE_MEDIA = "media";

    // Nivix: cancelación de una alerta SOS activada desde la app (ver DeviceResource.updatePanic).
    // La activación reutiliza TYPE_ALARM con alarm=sos, igual que el botón físico del GPS.
    public static final String TYPE_PANIC_CANCELLED = "panicCancelled";

    // Nivix: hash del token FCM del celular que originó el evento. NotificatorFirebase no le envía
    // el push a ese token (quien activa/cancela el SOS ya lo está viendo en pantalla). Se guarda
    // hasheado porque los atributos del evento son visibles para todos los usuarios del vehículo.
    public static final String KEY_SOURCE_TOKEN_HASH = "sourceTokenHash";

    public static String hashSourceToken(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Date eventTime;

    public Date getEventTime() {
        return eventTime;
    }

    public void setEventTime(Date eventTime) {
        this.eventTime = eventTime;
    }

    private long positionId;

    public long getPositionId() {
        return positionId;
    }

    public void setPositionId(long positionId) {
        this.positionId = positionId;
    }

    private long geofenceId = 0;

    public long getGeofenceId() {
        return geofenceId;
    }

    public void setGeofenceId(long geofenceId) {
        this.geofenceId = geofenceId;
    }

    private long maintenanceId = 0;

    public long getMaintenanceId() {
        return maintenanceId;
    }

    public void setMaintenanceId(long maintenanceId) {
        this.maintenanceId = maintenanceId;
    }

}

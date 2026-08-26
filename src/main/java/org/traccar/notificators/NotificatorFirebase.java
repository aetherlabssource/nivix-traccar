/*
 * Copyright 2018 - 2026 Anton Tananaev (anton@traccar.org)
 * Copyright 2018 Andrey Kunitsyn (andrey@traccar.org)
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
package org.traccar.notificators;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.AndroidNotification;
import com.google.firebase.messaging.ApnsConfig;
import com.google.firebase.messaging.Aps;
import com.google.firebase.messaging.CriticalSound;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.MulticastMessage;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.traccar.config.Config;
import org.traccar.config.Keys;
import org.traccar.model.Event;
import org.traccar.model.ObjectOperation;
import org.traccar.model.Position;
import org.traccar.model.User;
import org.traccar.notification.MessageException;
import org.traccar.notification.NotificationFormatter;
import org.traccar.notification.NotificationMessage;
import org.traccar.session.cache.CacheManager;
import org.traccar.storage.Storage;
import org.traccar.storage.query.Columns;
import org.traccar.storage.query.Condition;
import org.traccar.storage.query.Request;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

@Singleton
public class NotificatorFirebase extends Notificator {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotificatorFirebase.class);

    // Alcance del entitlement de Alertas Críticas de Apple (com.apple.developer.usernotifications.critical-alerts),
    // aprobado solo para estos tipos de evento. Debe mantenerse sincronizado a mano con
    // criticalAlertEventTypes/criticalAlertAlarmCodes en nivix_app/lib/core/constants/app_constants.dart —
    // un tipo agregado en un solo lado solo produce "no suena crítico cuando debería" (fail-safe), nunca al revés.
    private static final List<String> CRITICAL_EVENT_TYPES = List.of("ignitionOn", "geofenceExit");
    private static final List<String> CRITICAL_ALARM_CODES = List.of("sos", "powerCut");

    private final Storage storage;
    private final CacheManager cacheManager;
    private final ObjectMapper objectMapper;
    private final String mode;
    private final FirebaseMessaging firebaseMessaging;

    @Inject
    public NotificatorFirebase(
            Config config, NotificationFormatter notificationFormatter,
            Storage storage, CacheManager cacheManager, ObjectMapper objectMapper) throws IOException {
        super(notificationFormatter);
        this.storage = storage;
        this.cacheManager = cacheManager;
        this.objectMapper = objectMapper;
        this.mode = config.getString(Keys.NOTIFICATOR_FIREBASE_MODE);

        InputStream serviceAccount = new ByteArrayInputStream(
                config.getString(Keys.NOTIFICATOR_FIREBASE_SERVICE_ACCOUNT).getBytes(StandardCharsets.UTF_8));

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                .build();

        firebaseMessaging = FirebaseMessaging.getInstance(
                FirebaseApp.initializeApp(options, "manager"));
    }

    private static boolean isCriticalEvent(Event event) {
        if (event == null) {
            return false;
        }
        String type = event.getType();
        if (CRITICAL_EVENT_TYPES.contains(type)) {
            return true;
        }
        if (Event.TYPE_ALARM.equals(type)) {
            return CRITICAL_ALARM_CODES.contains(event.getString(Position.KEY_ALARM));
        }
        return false;
    }

    @Override
    public void send(User user, NotificationMessage message, Event event, Position position) throws MessageException {
        if (user.hasAttribute("notificationTokens")) {

            List<String> registrationTokens = new ArrayList<>(
                    Arrays.asList(user.getString("notificationTokens").split("[, ]")));

            var androidConfig = AndroidConfig.builder()
                    .setNotification(AndroidNotification.builder().setSound("default").build());

            boolean critical = isCriticalEvent(event);

            var apsBuilder = Aps.builder();
            if (critical) {
                // Sonido "critical" marcado en el propio payload APNs: es lo único que hace que iOS
                // bypasee silencio/No Molestar cuando la entrega ocurre con la app en background o
                // terminada (el sistema pinta la notificación directo desde este payload sin darle
                // control al cliente sobre el sonido en ese caso). Requiere el entitlement
                // com.apple.developer.usernotifications.critical-alerts en el bundle destino.
                apsBuilder.setSound(CriticalSound.builder()
                        .setCritical(true)
                        .setName("default")
                        .setVolume(1.0)
                        .build());
            } else {
                apsBuilder.setSound("default");
            }
            var apnsConfig = ApnsConfig.builder().setAps(apsBuilder.build());

            if (message.priority() || critical) {
                androidConfig.setPriority(AndroidConfig.Priority.HIGH);
                apnsConfig.putHeader("apns-priority", "10");
            }

            var messageBuilder = MulticastMessage.builder()
                    .setAndroidConfig(androidConfig.build())
                    .setApnsConfig(apnsConfig.build())
                    .addAllTokens(registrationTokens);

            if (!"data".equals(mode)) {
                messageBuilder.setNotification(com.google.firebase.messaging.Notification.builder()
                        .setTitle(message.subject())
                        .setBody(message.digest())
                        .build());
            }

            if (event != null) {
                messageBuilder.putData("eventId", String.valueOf(event.getId()));
                if (!"direct".equals(mode)) {
                    try {
                        messageBuilder.putData("event", objectMapper.writeValueAsString(event));
                        if (position != null) {
                            messageBuilder.putData("position", objectMapper.writeValueAsString(position));
                        }
                    } catch (JsonProcessingException e) {
                        LOGGER.warn("Firebase data serialization error", e);
                    }
                }
            }

            try {
                var result = firebaseMessaging.sendEachForMulticast(messageBuilder.build());
                List<String> failedTokens = new LinkedList<>();
                var iterator = result.getResponses().listIterator();
                while (iterator.hasNext()) {
                    int index = iterator.nextIndex();
                    var response = iterator.next();
                    if (!response.isSuccessful()) {
                        MessagingErrorCode error = response.getException().getMessagingErrorCode();
                        if (error == MessagingErrorCode.INVALID_ARGUMENT || error == MessagingErrorCode.UNREGISTERED) {
                            failedTokens.add(registrationTokens.get(index));
                        }
                        LOGGER.warn("Firebase user {} error", user.getId(), response.getException());
                    }
                }
                if (!failedTokens.isEmpty()) {
                    registrationTokens.removeAll(failedTokens);
                    if (registrationTokens.isEmpty()) {
                        user.removeAttribute("notificationTokens");
                    } else {
                        user.set("notificationTokens", String.join(",", registrationTokens));
                    }
                    storage.updateObject(user, new Request(
                            new Columns.Include("attributes"),
                            new Condition.Equals("id", user.getId())));
                    cacheManager.invalidateObject(true, User.class, user.getId(), ObjectOperation.UPDATE);
                }
            } catch (Exception e) {
                LOGGER.warn("Firebase error", e);
            }
        }
    }

}

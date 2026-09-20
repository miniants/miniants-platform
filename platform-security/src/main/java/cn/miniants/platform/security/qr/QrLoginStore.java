package cn.miniants.platform.security.qr;

import java.util.Optional;

public interface QrLoginStore {

    QrLoginSession create(String initiatingClientId);

    Optional<QrLoginSession> find(String scene);

    QrLoginSession markScanned(String scene, String provider, String subject);

    QrLoginSession markSuccess(String scene, String provider, String subject, String designatedUsername);

    void remove(String scene);
}

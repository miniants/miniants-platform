package cn.miniants.platform.integration.outbound;

public record OutboundResponse(int status, String body) {

    public boolean ok() {
        return status >= 200 && status < 400;
    }
}

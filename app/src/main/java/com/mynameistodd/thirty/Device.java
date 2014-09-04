package com.mynameistodd.thirty;

import com.parse.ParseClassName;
import com.parse.ParseObject;

import java.util.Date;

/**
 * Created by todd on 7/10/14.
 */
@ParseClassName("Device")
public class Device extends ParseObject {
    private String name;
    private String address;
    private Date capturedAt;

    public String getName() {
        return getString("name");
    }

    public void setName(String name) {
        put("name", name);
    }

    public String getAddress() {
        return getString("address");
    }

    public void setAddress(String address) {
        put("address", address);
    }

    public Date getCapturedAt() {
        return getDate("capturedAt");
    }

    public void setCapturedAt(Date capturedAt) {
        put("capturedAt", capturedAt);
    }

    public Device() {
    }
}

package com.mynameistodd.thirty;

import com.parse.ParseClassName;
import com.parse.ParseObject;

import java.util.Date;

/**
 * Created by todd on 9/5/14.
 */
@ParseClassName("DiagnosticData")
public class DiagnosticData extends ParseObject {
    private String name;
    private String value;
    private Date capturedAt;

    public String getName() {
        return getString("name");
    }

    public void setName(String name) {
        put("name", name);
    }

    public String getValue() {
        return getString("value");
    }

    public void setValue(String value) {
        put("value", value);
    }

    public Date getCapturedAt() {
        return getDate("capturedAt");
    }

    public void setCapturedAt(Date capturedAt) {
        put("capturedAt", capturedAt);
    }

    public DiagnosticData() {
    }
}

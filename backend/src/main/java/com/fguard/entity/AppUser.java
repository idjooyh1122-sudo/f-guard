package com.fguard.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "app_user")
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String displayName;
    private String homeLabel;
    private Double homeLat;
    private Double homeLng;
    private String campusLabel;
    private Double campusLat;
    private Double campusLng;
    private int prepMinutes;
    private int baseTravelMinutes;
    private int bufferMinutes;

    public Long getId() { return id; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String v) { displayName = v; }
    public String getHomeLabel() { return homeLabel; }
    public void setHomeLabel(String v) { homeLabel = v; }
    public Double getHomeLat() { return homeLat; }
    public void setHomeLat(Double v) { homeLat = v; }
    public Double getHomeLng() { return homeLng; }
    public void setHomeLng(Double v) { homeLng = v; }
    public String getCampusLabel() { return campusLabel; }
    public void setCampusLabel(String v) { campusLabel = v; }
    public Double getCampusLat() { return campusLat; }
    public void setCampusLat(Double v) { campusLat = v; }
    public Double getCampusLng() { return campusLng; }
    public void setCampusLng(Double v) { campusLng = v; }
    public int getPrepMinutes() { return prepMinutes; }
    public void setPrepMinutes(int v) { prepMinutes = v; }
    public int getBaseTravelMinutes() { return baseTravelMinutes; }
    public void setBaseTravelMinutes(int v) { baseTravelMinutes = v; }
    public int getBufferMinutes() { return bufferMinutes; }
    public void setBufferMinutes(int v) { bufferMinutes = v; }
}

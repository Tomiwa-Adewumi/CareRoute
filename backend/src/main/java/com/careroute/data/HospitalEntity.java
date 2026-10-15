package com.careroute.data;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "hospitals")
public class HospitalEntity {
    @Id
    private String id;
    private String name;
    private String city;
    private String province;
    private String address;
    private Double latitude;
    private Double longitude;
    private boolean emergencyDepartment;
    private boolean pediatricEmergency;
    private boolean active;

    protected HospitalEntity() {}

    public HospitalEntity(String id, String name, String city, String province, String address,
                          Double latitude, Double longitude, boolean emergencyDepartment,
                          boolean pediatricEmergency, boolean active) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.province = province;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.emergencyDepartment = emergencyDepartment;
        this.pediatricEmergency = pediatricEmergency;
        this.active = active;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCity() { return city; }
    public String getProvince() { return province; }
    public String getAddress() { return address; }
    public Double getLatitude() { return latitude; }
    public Double getLongitude() { return longitude; }
    public boolean isEmergencyDepartment() { return emergencyDepartment; }
    public boolean isPediatricEmergency() { return pediatricEmergency; }
    public boolean isActive() { return active; }
}

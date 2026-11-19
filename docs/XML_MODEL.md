# XML Data Model & Schema Definition

> *Note: This document details the Android XML UI layouts used in the mobile client, alongside an illustrative XML schema (XSD) representing a prospective telemetry data export format.*

## 1. Role of XML in SecureTrack
XML is extensively utilized across the SecureTrack project in two key areas:
1. **Android Application UI & Configuration**: Layouts (`activity_main.xml`, `activity_devices.xml`, `activity_splash.xml`), vectors, values (`strings.xml`, `colors.xml`), and the system manifest (`AndroidManifest.xml`).
2. **Standardized Device Telemetry Exchange (Illustrative Academic Model)**: Cross-platform interoperability format for exporting location telemetry and security incident logs.

---

## 2. Telemetry Exchange XML Sample (Illustrative)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<SecureTrackReport xmlns="https://securetrack.example.com/schema"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="https://securetrack.example.com/schema securetrack.xsd">
    <Owner uid="EXAMPLE_USER_UID">
        <Email>user@example.com</Email>
        <FullName>SecureTrack User</FullName>
    </Owner>
    <Device id="dev-sample-uuid-1234" status="ONLINE">
        <DeviceName>Samsung Galaxy A07 5G</DeviceName>
        <Model>SM-A076B</Model>
        <AndroidVersion>14</AndroidVersion>
        <BatteryLevel>84</BatteryLevel>
        <TelemetryFeed>
            <Location id="loc-101" source="GPS">
                <Latitude>12.971598</Latitude>
                <Longitude>77.594562</Longitude>
                <Accuracy>8.5</Accuracy>
                <Speed>0.0</Speed>
                <Timestamp>2026-11-19T17:15:00Z</Timestamp>
            </Location>
        </TelemetryFeed>
    </Device>
</SecureTrackReport>
```

---

## 3. XML Schema Definition (XSD)
```xml
<?xml version="1.0" encoding="UTF-8"?>
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema"
           targetNamespace="https://securetrack.example.com/schema"
           xmlns="https://securetrack.example.com/schema"
           elementFormDefault="qualified">

    <xs:element name="SecureTrackReport">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="Owner">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="Email" type="xs:string"/>
                            <xs:element name="FullName" type="xs:string"/>
                        </xs:sequence>
                        <xs:attribute name="uid" type="xs:string" use="required"/>
                    </xs:complexType>
                </xs:element>
                <xs:element name="Device" maxOccurs="unbounded">
                    <xs:complexType>
                        <xs:sequence>
                            <xs:element name="DeviceName" type="xs:string"/>
                            <xs:element name="Model" type="xs:string"/>
                            <xs:element name="AndroidVersion" type="xs:string"/>
                            <xs:element name="BatteryLevel" type="xs:int"/>
                            <xs:element name="TelemetryFeed">
                                <xs:complexType>
                                    <xs:sequence>
                                        <xs:element name="Location" maxOccurs="unbounded">
                                            <xs:complexType>
                                                <xs:sequence>
                                                    <xs:element name="Latitude" type="xs:double"/>
                                                    <xs:element name="Longitude" type="xs:double"/>
                                                    <xs:element name="Accuracy" type="xs:float"/>
                                                    <xs:element name="Speed" type="xs:float"/>
                                                    <xs:element name="Timestamp" type="xs:dateTime"/>
                                                </xs:sequence>
                                                <xs:attribute name="id" type="xs:string" use="required"/>
                                                <xs:attribute name="source" type="xs:string" use="optional"/>
                                            </xs:complexType>
                                        </xs:element>
                                    </xs:sequence>
                                </xs:complexType>
                            </xs:element>
                        </xs:sequence>
                        <xs:attribute name="id" type="xs:string" use="required"/>
                        <xs:attribute name="status" type="xs:string" use="required"/>
                    </xs:complexType>
                </xs:element>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
</xs:schema>
```

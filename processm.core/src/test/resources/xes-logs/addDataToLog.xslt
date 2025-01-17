<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.xes-standard.org/">

    <xsl:output method="xml" indent="yes"/>

    <xsl:template match="*[name()='log']">
        <xsl:copy>
            <xsl:apply-templates/>
            <!-- an extra trace -->
            <xsl:element name="trace">
                <xsl:element name="string">
                    <xsl:attribute name="key">concept:name</xsl:attribute>
                    <xsl:attribute name="value">-1</xsl:attribute>
                </xsl:element>
                <xsl:element name="string">
                    <xsl:attribute name="key">cost:currency</xsl:attribute>
                    <xsl:attribute name="value">EUR</xsl:attribute>
                </xsl:element>
                <xsl:element name="event">
                    <xsl:element name="string">
                        <xsl:attribute name="key">concept:name</xsl:attribute>
                        <xsl:attribute name="value">invite reviewers</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="date">
                        <xsl:attribute name="key">time:timestamp</xsl:attribute>
                        <xsl:attribute name="value">2007-10-03T01:23:45Z</xsl:attribute> <!-- Wednesday -->
                    </xsl:element>
                    <xsl:element name="string">
                        <xsl:attribute name="key">org:resource</xsl:attribute>
                        <xsl:attribute name="value">Mike</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="string">
                        <xsl:attribute name="key">lifecycle:transition</xsl:attribute>
                        <xsl:attribute name="value">complete</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="string">
                        <xsl:attribute name="key">cost:currency</xsl:attribute>
                        <xsl:attribute name="value">EUR</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="float">
                        <xsl:attribute name="key">cost:total</xsl:attribute>
                        <xsl:attribute name="value">1.0</xsl:attribute>
                    </xsl:element>
                </xsl:element>
            </xsl:element>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*[name()='trace']">
        <xsl:copy>
            <xsl:if test="./*[name()='string' and @key='concept:name']/@value mod 2">
                <xsl:element name="float">
                    <xsl:attribute name="key">cost:total</xsl:attribute>
                    <xsl:attribute name="value">
                        <xsl:number value="count(./*[name()='event'])"/>
                    </xsl:attribute>
                </xsl:element>
            </xsl:if>
            <xsl:element name="string">
                <xsl:attribute name="key">cost:currency</xsl:attribute>
                <xsl:attribute name="value">EUR</xsl:attribute>
            </xsl:element>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="*[name()='event']">
        <xsl:copy>
            <xsl:choose>
                <xsl:when test="(count(../*) mod 2)">
                    <xsl:element name="float">
                        <xsl:attribute name="key">cost:total</xsl:attribute>
                        <xsl:attribute name="value">1.08</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="string">
                        <xsl:attribute name="key">cost:currency</xsl:attribute>
                        <xsl:attribute name="value">USD</xsl:attribute>
                    </xsl:element>
                </xsl:when>
                <xsl:otherwise>
                    <xsl:element name="float">
                        <xsl:attribute name="key">cost:total</xsl:attribute>
                        <xsl:attribute name="value">1.0</xsl:attribute>
                    </xsl:element>
                    <xsl:element name="string">
                        <xsl:attribute name="key">cost:currency</xsl:attribute>
                        <xsl:attribute name="value">EUR</xsl:attribute>
                    </xsl:element>
                </xsl:otherwise>
            </xsl:choose>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet> 
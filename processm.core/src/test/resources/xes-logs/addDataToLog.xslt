<?xml version="1.0"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform" xmlns="http://www.xes-standard.org/">


    <xsl:template match="*[name()='trace']">
        <xsl:copy>
            <xsl:element name="float">
                <xsl:attribute name="key">cost:total</xsl:attribute>
                <xsl:attribute name="value">
                    <xsl:number value="count(./*[name()='event'])"/>
                </xsl:attribute>
            </xsl:element>
            <xsl:apply-templates/>
        </xsl:copy>
    </xsl:template>

    <xsl:template match="@*|node()">
        <xsl:copy>
            <xsl:apply-templates select="@*|node()"/>
        </xsl:copy>
    </xsl:template>

</xsl:stylesheet> 
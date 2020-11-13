package org.nervos.gw.utils

import net.sf.scuba.util.Hex
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1Set
import org.bouncycastle.asn1.ASN1TaggedObject
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.x509.Certificate
import org.bouncycastle.util.encoders.Base64
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.InputStreamReader
import java.util.HashMap

object CSCAMasterUtil {
    private var startFlag = "CscaMasterListData::"
    @Throws(Exception::class)
    fun analysisMasterList(inputStream: InputStream?): Map<String, Certificate> {
        var cerString = ""
        val sb = StringBuilder()
        val certMaps: MutableMap<String, Certificate> = HashMap()
        val br = BufferedReader(InputStreamReader(inputStream))
        var line: String?
        while (br.readLine().also { line = it } != null) {
            if (line!!.contains(startFlag)) {
                sb.append(line!!.substring(startFlag.length))
                while (true) {
                    line = br.readLine()
                    if (null == line || line!!.startsWith("dn:")) { //结束
                        break
                    } else {
                        sb.append(line)
                    }
                }
                cerString = sb.toString()
                sb.delete(0, sb.length)
                val certBytes = Base64.decode(cerString.replace("\\s*".toRegex(), ""))
                var bIn = ByteArrayInputStream(certBytes)
                var aIn = ASN1InputStream(bIn)
                var aP = aIn.readObject()
                var aS = ASN1Sequence.getInstance(aP)
                var aT = ASN1TaggedObject.getInstance(aS.getObjectAt(1))
                aP = ASN1Sequence.getInstance(aT.getObject().toASN1Primitive()).getObjectAt(2)
                    .toASN1Primitive()
                aS = ASN1Sequence.getInstance(aP)
                aT = ASN1TaggedObject.getInstance(aS.getObjectAt(1))
                val derOS = aT.getObject() as DEROctetString
                bIn = ByteArrayInputStream(Hex.hexStringToBytes(derOS.toString().substring(1)))
                aIn = ASN1InputStream(bIn)
                while (aIn.readObject().also { aP = it } != null) {
                    val asn1 = ASN1Sequence.getInstance(aP)
                    require(!(asn1 == null || asn1.size() == 0)) { "null or empty sequence passed." }
                    require(asn1.size() == 2) { "Incorrect sequence size: " + asn1.size() }
                    val certSet = ASN1Set.getInstance(asn1.getObjectAt(1))
                    for (i in 0 until certSet.size()) {
                        val certificate = Certificate.getInstance(certSet.getObjectAt(i))
                        val sn = Hex.bytesToHexString(certificate.serialNumber.encoded)
                        certMaps[sn] = certificate
                    }
                }
                aIn.close()
            }
        }
        br.close()
        return certMaps
    }
}
package org.nervos.gw.passport

import android.os.Parcelable
import kotlinx.android.parcel.Parcelize
import net.sf.scuba.data.Gender

@Parcelize
class Person(var firstName: String = "", var lastName: String = "", var gender: Gender = Gender.MALE) : Parcelable

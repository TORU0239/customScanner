package sg.toru.customscanner

import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import java.util.regex.Pattern

/**
 * A simple [Fragment] subclass.
 */
class PassportFragment : Fragment() {

    private val passportInfo:String by lazy {
        arguments?.getString(KEY)?:""
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_passport, container, false)
    }

    override fun onViewCreated(
        view: View,
        savedInstanceState: Bundle?
    ) {
        super.onViewCreated(view, savedInstanceState)
        val text = view.findViewById<TextView>(R.id.txt_passport)
        determinePassportInfo(passportInfo, text)
    }

    private fun fetchInfoForFirstRow(row:String, textview:TextView){
        Log.i("Toru", "row:: $row")
        val country = row.substring(2, 5)
        val name = row.substring(5, row.length)
        var number = 0
        for( c in name){
            if(c == '<'){
                break
            }
            number += 1
        }
        val surname = name.substring(0, number)
        val givenName = name.substring(number, name.length).filter { c ->
            c != '<'
        }
        textview.text = "country: $country,\nsurname: $surname,\ngiven name: $givenName"
    }

    private fun determinePassportInfo(fullRead:String, textview:TextView){
        if(fullRead.length != 88){
            Log.i("Toru", "wrong format.")
            return
        }
        else{
            // test code
            val list = fullRead.substring(0,44)
            val list2 = fullRead.substring(44, fullRead.length)
            fetchInfoForFirstRow(list, textview)
        }

        val patternLineOldPassportType = Pattern.compile(REGEX_OLD_PASSPORT)
        val matcherLineOldPassportType = patternLineOldPassportType.matcher(fullRead)
        if (matcherLineOldPassportType.find()) {
            //Old passport format
            Log.i("Toru", "group count:: ${matcherLineOldPassportType.groupCount()}")
            val line2 = matcherLineOldPassportType.group(0)
            Log.i("Toru", "line:: $line2")
            line2?.let { fullPassport ->
                var documentNumber = fullPassport.substring(0, 9)
                val nationality = fullPassport.substring(10,13)
                val dateOfBirthDay = fullPassport.substring(13, 19)
                val gender = fullPassport.substring(20,21)
                val expirationDate = fullPassport.substring(21, 27)

                // need
                // 1.name, 2.gender, 3.date of birth, 4.nationality, 5.passport number, 6.expiry date

                //As O and 0 and really similar most of the countries just removed them from the passport, so for accuracy I am formatting it
                documentNumber = documentNumber.replace("O".toRegex(), "0")
                textview.text = textview.text.toString().plus(", \n")
                                    .plus("$documentNumber,\n")
                                    .plus("Nationality: $nationality,\n")
                                    .plus("Birth Of Date: $dateOfBirthDay,\n")
                                    .plus("Expiration Date: $expirationDate,\n")
                                    .plus("Gender: $gender\n")
                                    .plus(" from old passport")
            }
        }
        else {
            //Try with the new IP passport type
            val patternLineIPassportTypeLine1 = Pattern.compile(REGEX_IP_PASSPORT_LINE_1)
            val matcherLineIPassportTypeLine1 = patternLineIPassportTypeLine1.matcher(fullRead)
            val patternLineIPassportTypeLine2 = Pattern.compile(REGEX_IP_PASSPORT_LINE_2)
            val matcherLineIPassportTypeLine2 = patternLineIPassportTypeLine2.matcher(fullRead)
            if (matcherLineIPassportTypeLine1.find() && matcherLineIPassportTypeLine2.find()) {
                val line1 = matcherLineIPassportTypeLine1.group(0)
                val line2 = matcherLineIPassportTypeLine2.group(0)

                line1?.let { first ->
                    line2?.let { second ->
                        var documentNumber = first.substring(5, 14)
                        val dateOfBirthDay = second.substring(0, 6)
                        val expirationDate = second.substring(8, 14)

                        //As O and 0 and really similar most of the countries just removed them from the passport, so for accuracy I am formatting it
                        documentNumber = documentNumber.replace("O".toRegex(), "0")
                        textview.text = documentNumber
                                            .plus(", $dateOfBirthDay")
                                            .plus(", $expirationDate")
                                            .plus(" from new passport")
                    }
                }
            }
            else {
                //No success
            }
        }
    }

    companion object{
        private const val KEY = "passport_info"
        private const val REGEX_OLD_PASSPORT = "[A-Z0-9<]{9}[0-9]{1}[A-Z<]{3}[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}"
        private const val REGEX_IP_PASSPORT_LINE_1 = "\\bIP[A-Z<]{3}[A-Z0-9<]{9}[0-9]{1}"
        private const val REGEX_IP_PASSPORT_LINE_2 = "[0-9]{6}[0-9]{1}[FM<]{1}[0-9]{6}[0-9]{1}[A-Z<]{3}"

        @JvmStatic
        fun newInstance(passportMRZ:String):PassportFragment{
            val fragment = PassportFragment()
            fragment.arguments = Bundle().apply {
                putString(KEY, passportMRZ)
            }
            return fragment
        }
    }
}
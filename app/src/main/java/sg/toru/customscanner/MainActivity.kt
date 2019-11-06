package sg.toru.customscanner

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //        val passportSample
//                = "PMKORCHOI<<TORABDCE<<<<<<<<<<<<<<<<<<<<<<<<<"+ "M345623455KOR8111115M230314012345679V15983696"

        val passportSample
                = "P<PHLCHUN<<ALLEN<CHUNG<MARCOS<<<<<<<<<<<<<<"+
                "EC12345678PHL9111125M1234567<<<<<<<<<<<<<<00"
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.container_fragment,
                PassportFragment.newInstance(passportSample)
            ).commit()
    }
}
package sg.toru.customscanner

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val passportSample = "USE YOUR OWN PASSPORT"
        supportFragmentManager.beginTransaction()
            .replace(
                R.id.container_fragment,
                PassportFragment.newInstance(passportSample)
            ).commit()
    }
}
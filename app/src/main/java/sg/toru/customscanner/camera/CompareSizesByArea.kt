package sg.toru.customscanner.camera

import android.util.Size
import java.lang.Integer.signum

internal class CompareSizesByArea:Comparator<Size> {
    override fun compare(lhs: Size, rhs: Size): Int = signum(lhs.width * lhs.height - rhs.width * rhs.height)
}
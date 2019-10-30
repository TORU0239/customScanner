/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sg.toru.customscanner.camera

import android.content.Context
import android.view.TextureView
import android.util.AttributeSet

class AutoFitTextureView: TextureView {
    constructor(context:Context):super(context, null)
    constructor(context: Context, attrs: AttributeSet?):super(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyle: Int):super(context, attrs, defStyle)

    private var ratioWidth:Int = 0
    private var ratioHeight:Int = 0

    fun aspectRatio(
        width:Int,
        height:Int
    ){
        ratioWidth = width
        ratioHeight = height
        requestLayout()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)

        if(0 == ratioHeight || 0 == ratioWidth){
            setMeasuredDimension(width, height)
        }
        else{
            if(width < height * ratioHeight / ratioWidth){
                setMeasuredDimension(width, width * ratioHeight / ratioWidth)
            }
            else{
                setMeasuredDimension(height * ratioWidth / ratioHeight, height)
            }
        }
    }
}

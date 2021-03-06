/*
   Copyright 2014 Julian Shen

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
 */

/*
  Changes made to the original file:
  - Changed CircleTransform to CutOutTriangleTransform

  Moficiations copyright (c) 2017 Elyasin Shaladi

  The original license applies, see below, Apache 2.0.
 */

package com.example.android.popularmovies.transform;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;

import com.squareup.picasso.Transformation;

/**
 * Describes a transformation to cut out a triangle from the upper left corner of a bitmap.
 */
public class CutOutTriangleTransform implements Transformation {

    private static final String LOG_TAG = CutOutTriangleTransform.class.getName();

    /**
     * Cuts out an upper left (triangle) corner from the bitmap and paints it white.
     *
     * @param source The source image
     * @return The image with the upper left corner (triangle) painted in white.
     */
    @Override
    public Bitmap transform(Bitmap source) {

        Bitmap mutableBitmap = Bitmap.createBitmap(source.getWidth(), source.getHeight(), source.getConfig());
        Canvas canvas = new Canvas(mutableBitmap);

        BitmapShader shader = new BitmapShader(source, BitmapShader.TileMode.CLAMP, BitmapShader.TileMode.CLAMP);

        Paint paint = new Paint();
        paint.setShader(shader);
        paint.setAntiAlias(true);
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);

        Path path = new Path();
        path.moveTo(source.getWidth() / 2, 0);
        path.lineTo(source.getWidth(), 0);
        path.lineTo(source.getWidth(), source.getHeight());
        path.lineTo(0, source.getHeight());
        path.lineTo(0, source.getWidth() / 2);
        path.lineTo(source.getWidth() / 2, 0);
        path.close();

        canvas.drawPath(path, paint);

        source.recycle();

        return mutableBitmap;
    }

    /**
     * TODO How can I determine a unique key for a bitmap?
     *
     * @return The objec's hashCode. This is not a good choice, but not a big deal neither.
     */
    @Override
    public String key() {
        return String.valueOf(this.hashCode());
    }
}
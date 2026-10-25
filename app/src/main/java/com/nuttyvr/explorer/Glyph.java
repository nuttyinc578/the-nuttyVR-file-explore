package com.nuttyvr.explorer;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Lightweight vector marks; no bitmap decoding or network assets in the file grid. */
final class Glyph extends View {
    String kind;
    int tint;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final Path path = new Path();
    public Glyph(Context c) { this(c, "file", 0xff9baec4); }
    Glyph(Context c, String kind, int tint) { super(c); this.kind = kind; this.tint = tint; setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); }
    private void line(Canvas c, float x, float y, float xx, float yy) { c.drawLine(x,y,xx,yy,p); }
    private void box(Canvas c, float x, float y, float xx, float yy, float radius) {
        rect.set(x,y,xx,yy); c.drawRoundRect(rect,radius,radius,p);
    }
    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float size = Math.min(getWidth(), getHeight());
        c.save(); c.translate((getWidth()-size)/2f,(getHeight()-size)/2f); c.scale(size/32f,size/32f);
        p.setColor(tint); p.setStrokeWidth(2); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); p.setStyle(Paint.Style.STROKE);
        path.reset();
        switch (kind) {
            case "": break;
            case "folder": {
                p.setStyle(Paint.Style.FILL); p.setAlpha(45); box(c,3,9,29,27,4);
                p.setAlpha(255); p.setStyle(Paint.Style.STROKE);
                path.moveTo(3,24); path.lineTo(3,8); path.quadTo(3,5,6,5);
                path.lineTo(12,5); path.lineTo(16,9); path.lineTo(26,9); path.quadTo(29,9,29,12);
                path.lineTo(29,24); path.quadTo(29,27,26,27); path.lineTo(6,27); path.quadTo(3,27,3,24); c.drawPath(path,p);
                line(c,4,13,28,13); break;
            }
            case "logo":
                box(c,3,8,29,25,6); line(c,6,8,10,5); line(c,26,8,22,5);
                box(c,8,13,13,18,1); box(c,19,13,24,18,1); line(c,13,22,19,22); break;
            case "image":
                box(c,4,4,28,28,4); c.drawCircle(11,11,2,p); line(c,5,24,13,16); line(c,13,16,18,21); line(c,18,21,23,16); line(c,23,16,28,21); break;
            case "video":
                box(c,3,5,29,27,4); path.moveTo(13,11); path.lineTo(22,16); path.lineTo(13,22); path.close(); c.drawPath(path,p); break;
            case "music":
                line(c,14,23,14,8); line(c,14,8,27,5); line(c,27,5,27,20); c.drawOval(5,21,14,28,p); c.drawOval(18,18,27,25,p); break;
            case "apk":
                box(c,5,10,27,26,4); line(c,9,10,6,5); line(c,23,10,26,5); c.drawPoint(11,16,p); c.drawPoint(21,16,p); line(c,11,22,21,22); break;
            case "zip":
                box(c,7,3,25,29,3); line(c,16,4,16,9); line(c,16,14,16,17); box(c,13,20,19,25,1); break;
            case "storage":
                box(c,5,4,27,28,4); line(c,6,20,26,20); c.drawPoint(11,24,p); c.drawPoint(16,24,p); break;
            case "search": c.drawCircle(14,14,9,p); line(c,21,21,28,28); break;
            case "grid": box(c,5,5,13,13,2); box(c,19,5,27,13,2); box(c,5,19,13,27,2); box(c,19,19,27,27,2); break;
            case "list": for(int y=8;y<=24;y+=8){c.drawPoint(5,y,p);line(c,11,y,27,y);} break;
            case "up": line(c,16,27,16,5); line(c,7,14,16,5); line(c,25,14,16,5); break;
            case "back": line(c,26,16,6,16); line(c,14,7,5,16); line(c,14,25,5,16); break;
            case "arrow": line(c,12,7,21,16); line(c,21,16,12,25); break;
            case "down": line(c,16,4,16,23); line(c,9,16,16,23); line(c,23,16,16,23); line(c,5,28,27,28); break;
            case "plus": line(c,16,6,16,26); line(c,6,16,26,16); break;
            case "check": line(c,6,16,13,23); line(c,13,23,27,8); break;
            case "close": line(c,8,8,24,24); line(c,8,24,24,8); break;
            case "more": c.drawPoint(7,16,p); c.drawPoint(16,16,p); c.drawPoint(25,16,p); break;
            case "sort": line(c,5,8,27,8); line(c,5,16,21,16); line(c,5,24,14,24); break;
            case "copy": box(c,11,10,27,28,3); line(c,21,5,6,5); line(c,6,5,6,22); break;
            case "cut": c.drawCircle(8,24,4,p); c.drawCircle(24,24,4,p); line(c,10,20,25,4); line(c,22,20,7,4); break;
            case "trash": box(c,8,9,24,28,3); line(c,5,8,27,8); line(c,12,4,20,4); line(c,13,14,13,23); line(c,19,14,19,23); break;
            case "info": c.drawCircle(16,16,12,p); c.drawPoint(16,10,p); line(c,16,15,16,23); break;
            case "refresh": c.drawArc(5,5,27,27,35,290,false,p); line(c,27,6,27,14); line(c,27,14,19,14); break;
            case "star": {
                for(int i=0;i<10;i++){double a=-Math.PI/2+i*Math.PI/5;float r=i%2==0?13:6;float x=16+(float)Math.cos(a)*r,y=16+(float)Math.sin(a)*r;if(i==0)path.moveTo(x,y);else path.lineTo(x,y);}path.close();c.drawPath(path,p);break;
            }
            default:
                path.moveTo(8,3);path.lineTo(20,3);path.lineTo(27,10);path.lineTo(27,28);path.lineTo(8,28);path.close();c.drawPath(path,p);
                line(c,19,4,19,11);line(c,19,11,26,11);line(c,12,17,22,17);line(c,12,22,20,22);
        }
        c.restore();
    }
}

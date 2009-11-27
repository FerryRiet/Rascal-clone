module experiments::VL::VLCore
import Integer;
import List;
import Set;
import IO;

alias Color = int;

@doc{Gray color (0-255)}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public void java gray(int gray);

@doc{Gray color (0-255) with transparency}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public Color java gray(int gray, real alpha);

@doc{Gray color as percentage (0.0-1.0)}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public void java gray(real perc);

@doc{Gray color with transparency}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public Color java gray(real perc, real alpha);

@doc{Named color}
@reflect{Needs calling context when generating an exception}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public Color java color(str colorName);

@doc{Named color with transparency}
@reflect{Needs calling context when generating an exception}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public Color java color(str colorName, real alpha);

@doc{RGB color}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public Color java rgb(int r, int g, int b);

@doc{RGB color with transparency}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public Color java rgb(int r, int g, int b, real alpha);

@doc{Interpolate two colors (in RGB space)}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public list[Color] java interpolateColor(Color from, Color to, real percentage);

@doc{Create a list of interpolated colors}
@javaClass{org.meta_environment.rascal.library.experiments.VL.VL}
public list[Color] java colorSteps(Color from, Color to, int steps);

@doc{Create a colorscale}
public Color(int) colorScale(list[int] values, Color from, Color to){
   mn = min(values);
   range = max(values) - mn;
   sc = colorSteps(from, to, 10);
   return Color(int v) { return sc[(9 * (v - mn)) / range]; };
}

data VPROP =
/* sizes */
     width(int width)
   | height(int height)
   | height2(int height2)               // TODO: height(list[int] heights)
   | size(int size)                     // size of varies elems
   | visible(bool visible)				// is elem visible?
   | gap(int amount)                    // gap between elements in composition
   
/* direction and alignment */
   | horizontal()                       // horizontal composition
   | vertical()                         // vertical composition
   | top()                              // top alignment
   | center()                           // center alignment
   | bottom()                           // bottom alignment
   | left()                             // left alignment
   | right()                            // right alignment
   
/* transformations */
//   | move(int byX)        			// translate in X direction
//   | vmove(int byY)                     // translate in Y direction
//     | rotate(int angle)
//  | scale(real perc)

 
 /* line and border attributes */
   | lineWidth(int lineWidth)			// line width
   | lineColor(Color lineColor)		    // line color
   | lineColor(str colorName)           // named line color
   
   | fillColor(Color fillColor)			// fill color
   | fillColor(str colorName)           // named fill color
   
 /* text attributes */
   | text(str s)                        // the text itself
   | font(str fontName)                 // named font
   | fontSize(int size)                 // font size
   | textAngle(int angle)               // rotation
   
/* other */
   | name(str name)                     // name of elem (used in edges and layouts)
   | closed()    						// closed shapes
   | curved()                           // use curves instead of straight lines
   ;
   
data Vertex = 
     vertex(int x, int y)                // vertex in a shape
   | vertex(int x, int y, VELEM marker)  // vertex with marker
   ;
   
data VELEM = 
/* drawing primitives */
     rect(list[VPROP] props)			// rectangle
   | ellipse(list[VPROP] props)			// ellipse
   | label(list[VPROP] props)			// text label
   | edge(list[VPROP], str from, str to) // edge between between two elements
   
/* lines and curves */
   | shape(list[Vertex] points)
   | shape(list[VPROP] props,list[Vertex] points)
   
/* composition */
   | combine(list[VELEM] elems)
   | combine(list[VPROP] props, list[VELEM] elems)
   
   | overlay(list[VELEM] elems) 
   | overlay(list[VPROP] props, list[VELEM] elems)
   
   | grid(list[VELEM] elems) 
   | grid(list[VPROP] props, list[VELEM] elems)
   
   | pack(list[VELEM] elems) 
   | pack(list[VPROP] props, list[VELEM] elems)
   
   | graph(list[VPROP], list[VELEM] nodes, list[VELEM] edges)
   ;


#let dir(name, ..children) = (name: name, children: children.pos())

#let mydir(left, right, ..children) = dir(
  [#left #box(width: 1fr, repeat[.]) #right],
  ..children.pos(),
)

#let EMPTY  = 0
#let CORNER = 1
#let LINE   = 2
#let BRANCH = 3

#let row(style, levels, content) = block(above: 0pt, below: style.pad)[
  #context {
    let height = measure[.].height
    for level in levels {
      box(
        baseline: bottom,
        height: height,
        inset: (y: -style.pad/2),
        width: style.indent,
        if level == CORNER {
          curve(
            stroke: black + style.line-width,
            curve.move((style.line-x, 0%)),
            curve.line((style.line-x, style.line-y)),
            curve.line((100%, style.line-y)),
          )
        } else if level == LINE {
          curve(
            stroke: black + style.line-width,
            curve.move((style.line-x, 0%)),
            curve.line((style.line-x, 100%)),
          )
        } else if level == BRANCH {
          curve(
            stroke: black +style.line-width,
            curve.move((style.line-x, 0%)),
            curve.line((style.line-x, 100%)),
            curve.move((style.line-x, style.line-y)),
            curve.line((100%, style.line-y)),
          )
        },
      )
    }
  }
  #content
]

#let listing-inner(style, levels, dirs) = {
  if dirs.len() == 0 { return }
  
  for dir in dirs.slice(0, -1) {
    row(style, (..levels, BRANCH), dir.name)
    listing-inner(style, (..levels, LINE), dir.at("children", default: ()))
  }
  
  let dir = dirs.last()
  row(style, (..levels, CORNER), dir.name)
  listing-inner(style, (..levels, EMPTY), dir.at("children", default: ()))
}

#let dir-listing(
  ..dirs,
  pad: 4pt,
  indent: 1em,
  line-x: 50%,
  line-y: 50%,
  line-width: 1pt,
) = {
  set align(left)

  let style = (
    pad: pad,
    indent: indent,
    line-x: line-x,
    line-y: line-y,
    line-width: line-width,
  )

  for dir in dirs.pos() {
    row(style, (), dir.name)
    listing-inner(style, (), dir.children)
  }
}

#let cowboy = text(font: "Open Sauce One", size: 0.8em, weight: "medium")[Cowboy]
#let vdots = box(inset: (right: -2.5pt), move(sym.dots.v, dy:0.48pt))
// #let vdots = box(inset: -2pt, emoji.ghost)
// #let vdots = sym.dots.v
#show "Cowboy": cowboy
#dir-listing(
  pad: 6pt,
  line-y: 60%,
  dir(`tool/`, 
    mydir(`monitoring/`, [_Monitoring tool root_],
      mydir(`doc/`, [API documentation]),
      mydir(`doc/`, [Sources]),
      mydir(`test/`, [Unit tests]),
      mydir(underline[`Makefile`], [Makefile for compiling the tool]),
    ),
    mydir(`examples/`, [_Reusability claims examples_],
      mydir(underline[`Makefile`], [Makefile for compiling the examples]),
      mydir(vdots, [Other directories, ignore]),
    ),
    mydir(`experiments/`, [_Functionality outcomes examples_],
      mydir(`cowboy/`, [Cowboy third-party application case study]),
      mydir(`token/`, [Cowboy token server web application],
        mydir(vdots, [Other directories, ignore]),
      ),
      mydir(underline[`Makefile`], [Makefile for testing Cowboy]),
      mydir(vdots, [Other directories, ignore]),
    ),
    mydir(vdots, [Other directories, ignore]),
  )
)

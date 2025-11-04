package nocrtl.router.crossbar

import chisel3._
import chisel3.util._
import nocrtl.bundle.BodyFlit
import nocrtl.params.NodeParams
import nocrtl.router.buffer.BufferOutputBundle
import org.chipsalliance.cde.config.Parameters

class Crossbar(nodeP:NodeParams, vnStr:String)(implicit p:Parameters) extends Module {
  private val portToVnMap = nodeP.ports.flatMap(port => {
    val vns = port.link.flatMap(_.vns).filter(_.typeStr == vnStr)
    Option.when(vns.nonEmpty)((port.dirStr, vns.head))
  })
  val vnInwardPortMap = portToVnMap.map(elm => (elm._1, IO(Flipped(Decoupled(new BufferOutputBundle(elm._2))))))
  vnInwardPortMap.foreach({case(a, b) => b.suggestName(s"inward_${a.toLowerCase}")})

  val vnOutwardPortMap = portToVnMap.map(elm => (elm._1, IO(Decoupled(new BodyFlit(elm._2)))))
  vnOutwardPortMap.foreach({case(a, b) => b.suggestName(s"outward_${a.toLowerCase}")})
}

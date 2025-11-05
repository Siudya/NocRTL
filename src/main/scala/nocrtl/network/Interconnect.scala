package nocrtl.network

import chisel3._
import chisel3.experimental.noPrefix
import chisel3.util._
import nocrtl.bundle.{AxiLiteBundle, AxiLiteXbar, Packet}
import nocrtl.network.inout.{IcnBundle, InoutController}
import nocrtl.params.NocParamsKey
import nocrtl.router.Router
import org.chipsalliance.cde.config.Parameters

class Interconnect(implicit p:Parameters) extends Module {
  private val nocP = p(NocParamsKey)
  private val nodes = nocP.nodes.map(n => (n.id, n)).toMap

  private val routers = nodes.map(n => noPrefix {
    val r = Module(new Router(n._2))
    r.suggestName(s"r_${n._1}")
    (n._1, r)
  })

  val ios = (for(n <- nodes) yield noPrefix {
    val ios = (for(vn <- n._2("local").link.flatMap(_.vns)) yield noPrefix {
      val io = IO(new IcnBundle(vn))
      io.suggestName(s"router_${n._1}_${vn.typeStr}")
      (vn.typeStr, io)
    }).toMap
    (n._1, ios)
  })

  private val ioctrl = nodes.map(n => noPrefix {
    val ctrl = Module(new InoutController(n._2("local")))
    ctrl.suggestName(s"d_${n._1}")
    (n._1, ctrl)
  })

  val s_axi = IO(Flipped(new AxiLiteBundle))

  val axiAddrSeq = nodes.map({case(i, r) =>
    val addr = (r.id.toLong << p(NocParamsKey).routerSpaceBits)
    val mask = 0xFFFF_FFFF_FFFF_FFFFL << p(NocParamsKey).routerSpaceBits
    (r.id -> (addr, mask))
  }).toSeq
  private val xbar = Module(new AxiLiteXbar(1, axiAddrSeq.map(_._2)))
  xbar.s_axi.head <> s_axi

  private val cfgMap = (for(i <- axiAddrSeq.indices) yield {
    (axiAddrSeq(i)._1, xbar.m_axi(i))
  }).toMap

  for((id, r) <- routers) {
    for((k, v) <- ioctrl(id).ioMap) v <> ios(id)(k)
    for((k, v) <- r.links("local")) v <> ioctrl(id).links(k)
    for((k, v) <- r.links.filterNot(_._1 == "local")) {
      val portP = nodes(id)(k)
      val peerNode = portP.peerNode
      val peerPort = portP.peerPort
      val peerLinks = routers(peerNode.id).links(peerPort.dirStr)
      r.routerCfgMap(k) := peerNode.id.U
      r.s_axi <> cfgMap(id)
      println(s"router_${id}.${k.toLowerCase} -> router_${peerNode.id}.${peerPort.dirStr.toLowerCase}")
      for((ln, lk) <- v) {
        lk.outward <> peerLinks(ln).inward
      }
    }
    r.routerCfgMap("local") := id.U
  }
}

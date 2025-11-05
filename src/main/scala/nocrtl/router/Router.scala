package nocrtl.router

import chisel3._
import nocrtl.bundle.{AxiLiteBundle, AxiLiteXbar}
import nocrtl.params.{NocParamsKey, NodeParams}
import nocrtl.router.port.{LinkBufferExternalPortBundle, Port}
import org.chipsalliance.cde.config.Parameters

class Router(nodeP:NodeParams)(implicit p:Parameters) extends Module {
  override val desiredName = s"Router${nodeP.id}"

  private val vnSeq = nodeP.ports.flatMap(_.link).flatMap(_.vns)
  private val portSeq = nodeP.ports

  val s_axi = IO(Flipped(new AxiLiteBundle))

  val links = portSeq.map(pt => (pt.dirStr, pt.link.map(ln => (ln.vnStr, IO(new LinkBufferExternalPortBundle(ln)))).toMap)).toMap
  for((pn, lks) <- links) {
    for((ln, lnk) <- lks) {
      lnk.suggestName(s"port_${pn}_${ln}")
    }
  }

  val routerCfgMap = portSeq.map(pt => (pt.dirStr, IO(Input(UInt(p(NocParamsKey).idBits.W))))).toMap
  routerCfgMap.foreach({case(a, b) => b.suggestName(s"port_${a}_router")})

  private val vnToFabricMap = vnSeq.map(vn => (vn.typeStr, Module(new VNFabric(nodeP, vn.typeStr)))).toMap
  vnToFabricMap.foreach({case(a, b) => b.suggestName(s"vn_fabric_${a.toLowerCase}")})

  private val portMap = portSeq.map(pt => (pt.dirStr, Module(new Port(pt)))).toMap
  portMap.foreach({case(a, b) => b.suggestName(s"io_buf_${a.toLowerCase}")})

  private val vnIdMap = p(NocParamsKey).vnIdMap
  private val vnSpaceBits = p(NocParamsKey).vnSpaceBits
  private val vnIdBits = p(NocParamsKey).vnIdBits
  private val cfgAddrSeq = vnSeq.map(vn => (vn.typeStr, (vnIdMap(vn.typeStr).toLong << vnSpaceBits, ((1 << vnIdBits) - 1).toLong << vnSpaceBits)))
  private val axiXbar = Module(new AxiLiteXbar(1, cfgAddrSeq.map(_._2)))

  axiXbar.s_axi.head <> s_axi
  for((vn, i) <- vnSeq.zipWithIndex) {
    vnToFabricMap(vn.typeStr).s_axi <> axiXbar.m_axi(i)
  }

  for(port <- portSeq) {
    for(vn <- vnSeq) {
      portMap(port.dirStr).vnToCompMap(vn.typeStr) <> vnToFabricMap(vn.typeStr).portToCompPort(port.dirStr)
      portMap(port.dirStr).vnToInwardMap(vn.typeStr) <> vnToFabricMap(vn.typeStr).portToInwardMap(port.dirStr)
      portMap(port.dirStr).vnToOutwardMap(vn.typeStr) <> vnToFabricMap(vn.typeStr).portToOutwardMap(port.dirStr)
      vnToFabricMap(vn.typeStr).routerCfgMap(port.dirStr) := routerCfgMap(port.dirStr)
    }
  }
}

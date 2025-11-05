package nocrtl.network.inout

import chisel3._
import chisel3.util._
import nocrtl.bundle.{BodyFlit, HeadFlit, Packet}
import nocrtl.params.VnParams
import nocrtl.utils.MimoQueue
import org.chipsalliance.cde.config.Parameters

class FlitMerger(vnP:VnParams)(implicit p:Parameters) extends Module {
  val io = IO(new Bundle{
    val flit = Input(Decoupled(new BodyFlit(vnP)))
    val pack = Decoupled(new Packet(vnP))
  })

  private val nrFlits = vnP.packetSize
  private val mergeQueue = Module(new MimoQueue(new BodyFlit(vnP), nrFlits, 1, nrFlits + 1))

  mergeQueue.io.enq.head <> io.flit
  io.pack.valid := VecInit(mergeQueue.io.deq.map(_.valid)).asUInt.andR
  mergeQueue.io.deq.foreach(_.ready := io.pack.ready)

  when(mergeQueue.io.deq.last.valid) {
    assert(mergeQueue.io.deq.last.bits.tail)
  }

  private val headView = mergeQueue.io.deq.head.bits.asTypeOf(new HeadFlit(vnP))
  io.pack.bits.dst := headView.dest
  io.pack.bits.src := headView.sorc
  io.pack.bits.txn := headView.txn
  io.pack.bits.dat := headView.data

  if(nrFlits > 1) {
    io.pack.bits.dat := Cat((Seq(headView.data) ++ mergeQueue.io.deq.drop(1).map(_.bits.data)).reverse)
  }
}

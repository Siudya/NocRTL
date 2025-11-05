package nocrtl.network.inout

import chisel3._
import chisel3.util._
import nocrtl.bundle.{BodyFlit, HeadFlit, Packet}
import nocrtl.params.{NocParamsKey, VnParams}
import nocrtl.utils.{CircularQueuePtr, MimoQueue}
import org.chipsalliance.cde.config.Parameters

class PacketSplitter(vnP:VnParams)(implicit p:Parameters) extends Module {
  val io = IO(new Bundle{
    val pack = Flipped(Decoupled(new Packet(vnP)))
    val flit = Decoupled(new BodyFlit(vnP))
    val dest = Output(UInt(p(NocParamsKey).idBits.W))
    val vcoh = Option.when(vnP.vcs.size > 1)(Input(UInt(vnP.vcs.size.W)))
    val port = Input(UInt(p(NocParamsKey).portIdBits.W))
  })
  private class SlotPtr extends CircularQueuePtr[SlotPtr](vnP.packetSize)
  private object SlotPtr {
    def apply(f:Boolean, v:Int):SlotPtr = {
      val ptr = Wire(new SlotPtr)
      ptr.flag := f.B
      ptr.value := v.U
      ptr
    }
  }
  private val nrDeq = vnP.packetSize
  private val packQueue = Module(new Queue(new Packet(vnP), entries = 2))
  private val flitQueue = Module(new MimoQueue(new BodyFlit(vnP), nrDeq, 1, nrDeq * 2, true))
  private val slotPtrVec = RegInit(VecInit(Seq.tabulate(nrDeq)(i => SlotPtr(f = false, v = i))))
  private val slotPtrVecNext = WireInit(slotPtrVec)

  private val headFlit = Wire(new HeadFlit(vnP))
  private val dataVec = Option.when(nrDeq > 1)(io.pack.bits.dat(vnP.bodyDataBits - 1, vnP.headDataBits).asTypeOf(Vec(nrDeq - 1, UInt(vnP.width.W))))
  private val flitVec = Wire(Vec(nrDeq, new BodyFlit(vnP)))

  packQueue.io.enq <> io.pack
  io.flit <> flitQueue.io.deq.head
  slotPtrVec := slotPtrVecNext

  io.dest := packQueue.io.deq.bits.dst
  headFlit.head := true.B
  headFlit.tail := false.B
  headFlit.vc1h.foreach(_ := io.vcoh.get)
  headFlit.port := io.port
  headFlit.dest := packQueue.io.deq.bits.dst
  headFlit.sorc := packQueue.io.deq.bits.src
  headFlit.txn := packQueue.io.deq.bits.txn
  headFlit.data := packQueue.io.deq.bits.dat

  for(i <- flitVec.indices) {
    if(i == 0) {
      flitVec(0) := headFlit.asTypeOf(new BodyFlit(vnP))
    } else {
      flitVec(i).head := false.B
      flitVec(i).tail := (i == (nrDeq - 1)).B
      flitVec(i).vc1h.foreach(_ := io.vcoh.get)
      flitVec(i).data := dataVec.get(i - 1)
    }
  }
  if(nrDeq == 1) {
    flitQueue.io.enq.head.valid := packQueue.io.deq.valid
    flitQueue.io.enq.head.bits := flitVec.head
  } else {
    for(i <- flitQueue.io.enq.indices) {
      flitQueue.io.enq(i).valid := packQueue.io.deq.valid && slotPtrVec(i).flag === slotPtrVec.head.flag
      flitQueue.io.enq(i).bits.data := flitVec(slotPtrVec(i).value)
    }
    packQueue.io.deq.ready := flitQueue.io.enq.head.ready && slotPtrVecNext.head.value === 0.U
  }
  private val slotQueueEnqFire = flitQueue.io.enq.head.fire
  private val slotQueueEnqNum = PriorityEncoder(flitQueue.io.enq.map(e => !e.fire) :+ true.B)
  when(slotQueueEnqFire) {
    slotPtrVecNext.zip(slotPtrVec).foreach(elm => elm._1 := elm._2 + slotQueueEnqNum)
  }
}

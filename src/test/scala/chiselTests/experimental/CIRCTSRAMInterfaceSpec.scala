package chiselTests.experimental

import scala.util.chaining.scalaUtilChainingOps

import chisel3._
import chisel3.experimental.hierarchy.Instantiate

import chisel3.util.SRAM
import chisel3.util.experimental.{CIRCTSRAM, CIRCTSRAMInterface, CIRCTSRAMParameter, SlangUtils}

import chiselTests.ChiselFlatSpec
import circt.stage.ChiselStage

class CIRCTSRAMInterfaceSpec extends ChiselFlatSpec {
  "CIRCTSRAMInterface" should "match the Verilog ports generated by CIRCT" in {
    def matchPorts(rd: Int, wr: Int, rw: Int, depth: Int, width: Int, maskGranularity: Int) = {
      class GenerateSRAMModule extends Module {
        val sram = SRAM.masked(depth, Vec(width / maskGranularity, UInt(maskGranularity.W)), rd, wr, rw)

        val ioR = IO(chiselTypeOf(sram.readPorts)).tap(_.zip(sram.readPorts).foreach {
          case (io, mem) => io <> mem
        })
        val ioRW = IO(chiselTypeOf(sram.readwritePorts)).tap(_.zip(sram.readwritePorts).foreach {
          case (io, mem) => io <> mem
        })
        val ioW = IO(chiselTypeOf(sram.writePorts)).tap(_.zip(sram.writePorts).foreach {
          case (io, mem) => io <> mem
        })
      }

      class CIRCTSRAMTestModule
          extends CIRCTSRAM(CIRCTSRAMParameter("sram_interface", rd, wr, rw, depth, width, maskGranularity)) {
        class EmptyModule extends RawModule {}
        val memoryInstance = Instantiate(new EmptyModule)

        for (i <- 0 until rd) {
          io.R(i).data := DontCare
        }
        for (i <- 0 until rw) {
          io.RW(i).readData := DontCare
        }
      }

      val targetDir = "CIRCTSRAMInterfaceSpec"
      val firrtlOpts = Array("--split-verilog", s"-td=${targetDir}")
      ChiselStage.emitSystemVerilogFile(new GenerateSRAMModule, firrtlOpts)
      ChiselStage.emitSystemVerilogFile(new CIRCTSRAMTestModule, firrtlOpts)

      val sramPorts =
        SlangUtils.verilogModuleIO(
          SlangUtils.getVerilogAst(os.read(os.pwd / targetDir / s"sram_sram_${depth}x${width}.sv"))
        )
      val interfacePorts =
        SlangUtils.verilogModuleIO(SlangUtils.getVerilogAst(os.read(os.pwd / targetDir / "sram_interface.sv")))

      assert(sramPorts.toString == interfacePorts.toString)
    }

    Seq.tabulate(2, 2, 2) { case (rd, wr, rw) => if (rd + rw != 0 && wr + rw != 0) matchPorts(rd, wr, rw, 32, 8, 2) }
  }
}

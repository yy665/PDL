package pipedsl.common

object Utilities {

  def log2(x: Int): Int = {
    var y = x
    if (x < 0) { y = -y }
    var bits = 1;
    while (y > 1) {
      y = y >> 1
      bits += 1
    }
    bits
  }

  implicit class RichOption[A](opt: Option[A]) {
    def getOrThrow[T <: Throwable](except: T) = opt match {
      case Some(v) => v
      case None => throw except
    }
  }
}

object IgnoreWarning {
  def get[T](o: Option[T]): T = o match { case Some(t) => t }
}

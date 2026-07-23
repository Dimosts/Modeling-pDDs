name := "oscar-pdd-element2dnew"

scalaVersion := "2.13.11"

Compile / unmanagedJars += baseDirectory.value / "oscar-element.jar"

Compile / mainClass := Some("oscar.cp.mymodels.pDispersionElement2DNew")

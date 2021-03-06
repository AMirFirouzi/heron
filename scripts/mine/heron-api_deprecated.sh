 #! /bin/zsh
 
check_failure() {
  #compile check
  if [ $? != 0 ]; then
    echo "-@@@-FAILED: $1. \nexitting...\n"1>&2
    exit 1
  else
    echo "-@@@-SUCCESS: $1.\n"
  fi
}

module="$1"

version="0.14.4"
api="heron-api"
spi="heron-spi"
storm="heron-storm"

myheron_home="/home/amir/Projects/heron/myheron/heron"

myheron_pkgs_dir="bazel-bin/scripts/packages"
maven_api_dir="/home/amir/.m2/repository/com/twitter/heron/$api/$version"
maven_spi_dir="/home/amir/.m2/repository/com/twitter/heron/$spi/$version/"
maven_storm_dir="/home/amir/.m2/repository/com/twitter/heron/$storm/$version/"
 
echo "-@@@-changing directory to myheron..."
cd $myheron_home

echo "-@@@-start compiling heron..."
if [ $module=="api" ]; then
  bazel build  --config=ubuntu heron/api/src/java:heron-api
  check_failure "compile"
  
  echo "-@@@-clearing heron packages directory..."
  rm -rf "$myheron_home/$myheron_pkgs_dir/*"

  echo "-@@@-start building tar packages..."
  bazel build --config=ubuntu scripts/packages:tarpkgs-api
  check_failure "build tarpkgs"
else
  bazel build  --config=ubuntu heron/...
  check_failure "compile"
  echo "-@@@-clearing heron packages directory..."
  rm -rf "$myheron_home/$myheron_pkgs_dir/*"

  echo "-@@@-start building tar packages..."
  bazel build --config=ubuntu scripts/packages:tarpkgs
  check_failure "build tarpkgs"
fi

tar -xzf "$myheron_home/$myheron_pkgs_dir/$api.tar.gz" -C "$myheron_home/$myheron_pkgs_dir"
check_failure "unzip tarpkgs"


echo "-@@@-copying updated jars..."
cp -rf "$myheron_pkgs_dir/$api.jar" "$maven_api_dir/$api-$version.jar"
cp -rf "$myheron_pkgs_dir/$spi.jar" "$maven_spi_dir/$spi-$version.jar"
cp -rf "$myheron_pkgs_dir/$storm.jar" "$maven_storm_dir/$storm-$version.jar"
check_failure "copying updated jars"
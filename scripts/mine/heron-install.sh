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

version="SNAPSHOT"
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

if [ $module = "api" ]; then
  echo "-@@@-start compiling heron api..."
  bazel build  --config=ubuntu heron/api/src/java:heron-api
  check_failure "compile"
  
  echo "-@@@-start building and installing api packages..."
  bazel run --config=ubuntu -- scripts/packages:heron-api-install.sh --user --maven
  check_failure "heron-api-install"
else
  echo "-@@@-start compiling heron..."
  bazel build  --config=ubuntu heron/...
  check_failure "compile"

  echo "-@@@-start building and installing core packages..."
  bazel run --config=ubuntu -- scripts/packages:heron-client-install.sh --user
  check_failure "heron-client-install"

  echo "-@@@-start building and installing api packages..."
  bazel run --config=ubuntu -- scripts/packages:heron-api-install.sh --user --maven
  check_failure "heron-api-install"
fi

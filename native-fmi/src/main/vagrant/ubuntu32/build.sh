sed 's|config.vm.box.*|config.vm.box = "puphpet/ubuntu1404-x32"|g' ../ubuntu64/Vagrantfile > Vagrantfile.tmp
sed 's/mingw-w64/mingw32/g' Vagrantfile.tmp > Vagrantfile.tmp2
sed 's/JAVA_HOME64/JAVA_HOME32/g' Vagrantfile.tmp2 > Vagrantfile

rm *.tmp *.tmp2

repo=repo

vagrant up --provider parallels

target=/shared-host-folder-project-root/target/classes/lib/



vagrant ssh -c "rm -rf $repo && \
		cp -r /shared-host-git-repo/ $repo && \
    cd $repo && \
		mvn clean package -Pnative-linux32 -Pnative-win32 && \
		mkdir -p $target && \
		cp -rf native-fmi/target/fmu/binaries/Linux-i386 $target && \
  	cp -rf native-fmi/target/fmu/binaries/Windows-x86 $target"



vagrant halt


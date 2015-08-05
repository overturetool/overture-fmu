vagrant up --provider parallels

repo=repo

#   git clone /shared-host-git-repo $repo && \

target=/shared-host-folder-project-root/target/fmu/binaries/



vagrant ssh -c "rm -rf $repo && \
		cp -r /shared-host-git-repo/ $repo && \
    cd $repo && \
    mvn clean package -Pnative-linux64 -Pnative-win64 && \
		mkdir -p $target && \
		cp -rf native-fmi/target/fmu/binaries/Linux-amd64 $target && \
		cp -rf native-fmi/target/fmu/binaries/Windows-amd64 $target" 
		
vagrant halt


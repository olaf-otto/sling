#!/bin/sh

STAGING=${1}
DOWNLOAD=${2:-/tmp/sling-staging}
mkdir ${DOWNLOAD} 2>/dev/null

if [ -z "${STAGING}" -o ! -d "${DOWNLOAD}" ]
then
 echo "Usage: check_staged_release.sh <staging-number> [temp-directory]"
 exit
fi

if [ ! -e "${DOWNLOAD}/${STAGING}" ]
then
 echo "################################################################################"
 echo "                           DOWNLOAD STAGED REPOSITORY                           "
 echo "################################################################################"

 if [ `wget --help | grep "no-check-certificate" | wc -l` -eq 1 ]
 then
   CHECK_SSL=--no-check-certificate
 fi

 wget $CHECK_SSL \
  -nv -r -np "--reject=html,txt" "--follow-tags=" \
  -P "${DOWNLOAD}/${STAGING}" -nH "--cut-dirs=3" --ignore-length \
  "http://repository.apache.org/content/repositories/orgapachesling-${STAGING}/org/apache/sling/"

else
 echo "################################################################################"
 echo "                       USING EXISTING STAGED REPOSITORY                         "
 echo "################################################################################"
 echo "${DOWNLOAD}/${STAGING}"
fi

echo "################################################################################"
echo "                          CHECK SIGNATURES AND DIGESTS                          "
echo "################################################################################"

for i in `find "${DOWNLOAD}/${STAGING}" -type f | grep -v '\.\(asc\|sha1\|md5\)$'`
do
 f=`echo $i | sed 's/\.asc$//'`
 echo "$f"
 gpg --verify $f.asc 2>/dev/null
 if [ "$?" = "0" ]; then CHKSUM="GOOD"; else CHKSUM="BAD!!!!!!!!"; fi
 if [ ! -f "$f.asc" ]; then CHKSUM="----"; fi
 echo "gpg:  ${CHKSUM}"

 for tp in md5 sha1
 do
   A="`cat $f.$tp 2>/dev/null`"
   B="`openssl $tp < $f 2>/dev/null | sed 's/.*= *//' `"
   if [ "$A" = "$B" ]; then CHKSUM="GOOD (`cat $f.$tp`)"; else CHKSUM="BAD!! : $A not equal to $B"; fi
   echo "$tp : ${CHKSUM}"
 done

done

if [ -z "${CHKSUM}" ]; then echo "WARNING: no files found!"; fi

echo "################################################################################"



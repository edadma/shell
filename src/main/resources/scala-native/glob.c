#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <errno.h>
#include <glob.h>

int globerr(const char *path, int eerrno)
{
	fprintf(stderr, "%s: %s\n", path, strerror(eerrno));
	return 0;	/* let glob() keep going */
}

int
globHelper(char* pattern, glob_t** globbuf, size_t* pathc, char*** pathv) {
    *globbuf = malloc(sizeof(glob_t));

    int res = glob(pattern, 0, globerr, *globbuf);

    *pathc = (*globbuf)->gl_pathc;
    *pathv = (*globbuf)->gl_pathv;
    return res;
}

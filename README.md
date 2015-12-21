vertx-rest-storage
==================

[ ![Codeship Status for postit12/vertx-rest-storage](https://codeship.com/projects/29282ed0-ef1b-0132-1b5c-3628cb5d23b0/status?branch=master)](https://codeship.com/projects/84306)

Persistence for REST resources in the filesystem or a redis database. 

Stores resources in a hierarchical way according to their URI. It actually implements a generic CRUD REST service.

It uses usual mime mapping to determine content type so you can also use it as a web server. Without extension, JSON is assumed.

The following methods are supported on leaves (documents):
* GET: Returns the content of the resource.
* PUT: Stores the request body in the resource.
* DELETE: Deletes the resource.

The following methods are supported on intermediate nodes (collections):
* GET: Returns the list of collection members. Serves JSON and HTML representations.
* POST (Expand): Returns the expanded content of the sub resources of the (collection) resource. The depth is limited to 1 level.
* DELETE: Delete the collection and all its members.

Runs either as a module or can be integrated into an existing application by instantiating the RestStorageHandler class directly.

Configuration
-------------

    {
        "port": 8989        // Port we listen to. Defaults to 8989.
        "storage": ...,     // The type of storage (see below). Defaults to "filesystem".		                         
        "prefix": "/test",  // The part of the URL path before this handler (aka "context path" in JEE terminology). 
                            // Defaults to "/".
    }

### File System Storage

    {
        "storage": "filesystem",                         
        "root": "/test",    // The directory where the storage has its root (aka "document root" in Apache terminology).
                            // Defaults to "."
    }

### Redis Storage

	{
		"storage": "redis",      
		"address": "redis-client2" // The event bus address of the redis client busmod. 
		                           // Defaults to "redis-client".                  
		"root": "test",            // The prefix for the redis keys containing the data. 
		                           // Defaults to "rest-storage". 
		                        
	}
	
Caution: The redis storage implementation does not currently support streaming. Avoid transfering too big payloads since they will be entirely copied in memory.

Dependencies
------------

Redis persistence uses the redis-client busmod "de.marx-labs.redis-client". You must deploy it yourself.

Use gradle with alternative repositories
----------------------------------------
As standard the default maven repositories are set.
You can overwrite these repositories by setting these properties (`-Pproperty=value`):

* `repository` this is the repository where resources are fetched
* `uploadRepository` the repository used in `uploadArchives`
* `repoUsername` the username for uploading archives
* `repoPassword` the password for uploading archives


[![Bitdeli Badge](https://d2weczhvl823v0.cloudfront.net/lbovet/vertx-rest-storage/trend.png)](https://bitdeli.com/free "Bitdeli Badge")


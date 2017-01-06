// See the LICENCE.txt file distributed with this work for additional
// information regarding copyright ownership.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package reposiory

import redis.{ByteStringSerializer, RedisClient}
import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

trait RedisRepo {

  def db: RedisClient

  def del(key: String): Future[Long] = db.del(key)

  def upsert[V: ByteStringSerializer](key: String, value: V, expire: Option[Duration] = None) = db.set(key, value)

  def get(key: String)(implicit ec: ExecutionContext): Future[Option[String]] = db.get[String](key)

}
